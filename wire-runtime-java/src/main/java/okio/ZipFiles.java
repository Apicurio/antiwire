/*
 * Translated from okio/src/zlibMain/kotlin/okio/internal/ZipFiles.kt and
 * okio/src/zlibMain/kotlin/okio/internal/ZipEntry.kt at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices
 * below. Package-private in okio rather than okio.internal so it can construct the
 * package-private ZipFileSystem, matching upstream's same-module internal visibility.
 * The zip-creation half of the upstream file is out of the M0 subset: ZipFileSystem is
 * read-only, exactly like upstream.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.Inflater;

final class ZipFiles {

  private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x4034b50;
  private static final int CENTRAL_FILE_HEADER_SIGNATURE = 0x2014b50;
  private static final int END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x6054b50;
  private static final int ZIP64_LOCATOR_SIGNATURE = 0x07064b50;
  private static final int ZIP64_EOCD_RECORD_SIGNATURE = 0x06064b50;

  static final int COMPRESSION_METHOD_DEFLATED = 8;
  static final int COMPRESSION_METHOD_STORED = 0;

  /** General Purpose Bit Flags, Bit 0. Set if the file is encrypted. */
  private static final int BIT_FLAG_ENCRYPTED = 1 << 0;

  /**
   * General purpose bit flags that this implementation handles. Strict enforcement of additional
   * flags may break legitimate use cases.
   */
  private static final int BIT_FLAG_UNSUPPORTED_MASK = BIT_FLAG_ENCRYPTED;

  /** Max size of entries and archives without zip64. */
  private static final long MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE = 0xffffffffL;

  private static final int HEADER_ID_ZIP64_EXTENDED_INFO = 0x1;
  private static final int HEADER_ID_NTFS_EXTRA = 0x000a;
  private static final int HEADER_ID_EXTENDED_TIMESTAMP = 0x5455;

  private ZipFiles() {
  }

  /** A filter over zip entries; a null predicate keeps all entries. */
  interface EntryPredicate {
    boolean test(ZipEntry entry);
  }

  /**
   * Opens the file at {@code zipPath} for use as a file system. This uses UTF-8 for comments and
   * names in the zip file.
   */
  static ZipFileSystem openZip(Path zipPath, FileSystem fileSystem, EntryPredicate predicate)
      throws IOException {
    try (FileHandle fileHandle = fileSystem.openReadOnly(zipPath)) {
      // Scan backwards from the end of the file looking for the END_OF_CENTRAL_DIRECTORY_SIGNATURE.
      // If this file has no comment we'll see it on the first attempt; otherwise we have to go
      // backwards byte-by-byte until we reach it. (The number of bytes scanned will equal the
      // comment size).
      long scanOffset = fileHandle.size() - 22; // end of central directory record size is 22 bytes.
      if (scanOffset < 0L) {
        throw new IOException("not a zip: size=" + fileHandle.size());
      }
      long stopOffset = Math.max(scanOffset - 65_536L, 0L);
      long eocdOffset;
      EocdRecord record;
      String comment;
      while (true) {
        BufferedSource source = Okio.buffer(fileHandle.source(scanOffset));
        try {
          if (source.readIntLe() == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
            eocdOffset = scanOffset;
            record = readEocdRecord(source);
            comment = source.readUtf8((long) record.commentByteCount);
            break;
          }
        } finally {
          source.close();
        }

        scanOffset--;
        if (scanOffset < stopOffset) {
          throw new IOException("not a zip: end of central directory signature not found");
        }
      }

      // If this is a zip64, read a zip64 central directory record.
      long zip64LocatorOffset = eocdOffset - 20; // zip64 end of central directory locator is 20
      // bytes.
      if (zip64LocatorOffset > 0L) {
        BufferedSource zip64LocatorSource = Okio.buffer(fileHandle.source(zip64LocatorOffset));
        try {
          if (zip64LocatorSource.readIntLe() == ZIP64_LOCATOR_SIGNATURE) {
            int diskWithCentralDir = zip64LocatorSource.readIntLe();
            long zip64EocdRecordOffset = zip64LocatorSource.readLongLe();
            int numDisks = zip64LocatorSource.readIntLe();
            if (numDisks != 1 || diskWithCentralDir != 0) {
              throw new IOException("unsupported zip: spanned");
            }
            BufferedSource zip64EocdSource = Okio.buffer(
                fileHandle.source(zip64EocdRecordOffset));
            try {
              int zip64EocdSignature = zip64EocdSource.readIntLe();
              if (zip64EocdSignature != ZIP64_EOCD_RECORD_SIGNATURE) {
                throw new IOException("bad zip: expected 0x"
                    + Integer.toHexString(ZIP64_EOCD_RECORD_SIGNATURE)
                    + " but was 0x" + Integer.toHexString(zip64EocdSignature));
              }
              record = readZip64EocdRecord(zip64EocdSource, record);
            } finally {
              zip64EocdSource.close();
            }
          }
        } finally {
          zip64LocatorSource.close();
        }
      }

      // Seek to the first central directory entry and read all of the entries.
      List<ZipEntry> entries = new ArrayList<>();
      BufferedSource source = Okio.buffer(fileHandle.source(record.centralDirectoryOffset));
      try {
        for (long i = 0; i < record.entryCount; i++) {
          ZipEntry entry = readCentralDirectoryZipEntry(source);
          if (entry.offset >= record.centralDirectoryOffset) {
            throw new IOException(
                "bad zip: local file header offset >= central directory offset");
          }
          if (predicate == null || predicate.test(entry)) {
            entries.add(entry);
          }
        }
      } finally {
        source.close();
      }

      // Organize the entries into a tree.
      Map<Path, ZipEntry> index = buildIndex(entries);

      return new ZipFileSystem(zipPath, fileSystem, index, comment);
    }
  }

  /**
   * Returns a map containing all of {@code entries}, plus parent entries required so that all
   * entries (other than the file system root {@code /}) have a parent.
   */
  private static Map<Path, ZipEntry> buildIndex(List<ZipEntry> entries) {
    Path root = Path.get("/");
    Map<Path, ZipEntry> result = new LinkedHashMap<>();
    result.put(root, new ZipEntry(root, true, "", -1L, -1L, -1L, -1, -1L, -1, -1));

    // Iterate in sorted order so each path is preceded by its parent.
    List<ZipEntry> sortedEntries = new ArrayList<>(entries);
    sortedEntries.sort(Comparator.comparing(entry -> entry.canonicalPath));
    for (ZipEntry entry : sortedEntries) {
      // Note that this may clobber an existing element in the map. For consistency with
      // java.util.zip and java.nio.file.FileSystem, this prefers the last-encountered element.
      ZipEntry replaced = result.put(entry.canonicalPath, entry);
      if (replaced != null) {
        continue;
      }

      // Make sure this parent directories exist all the way up to the file system root.
      ZipEntry child = entry;
      while (true) {
        Path parentPath = child.canonicalPath.getParent();
        if (parentPath == null) {
          break; // child is '/'.
        }
        ZipEntry parentEntry = result.get(parentPath);

        // We've found a parent that already exists! Add the child; we're done.
        if (parentEntry != null) {
          parentEntry.children.add(child.canonicalPath);
          break;
        }

        // A parent is missing! Synthesize one.
        parentEntry = new ZipEntry(parentPath, true, "", -1L, -1L, -1L, -1, -1L, -1, -1);
        result.put(parentPath, parentEntry);
        parentEntry.children.add(child.canonicalPath);
        child = parentEntry;
      }
    }

    return result;
  }

  /** When this returns, {@code source} will be positioned at the start of the next entry. */
  private static ZipEntry readCentralDirectoryZipEntry(BufferedSource source) throws IOException {
    int signature = source.readIntLe();
    if (signature != CENTRAL_FILE_HEADER_SIGNATURE) {
      throw new IOException("bad zip: expected 0x"
          + Integer.toHexString(CENTRAL_FILE_HEADER_SIGNATURE)
          + " but was 0x" + Integer.toHexString(signature));
    }

    source.skip(4); // version made by (2) + version to extract (2).
    int bitFlag = source.readShortLe() & 0xffff;
    if ((bitFlag & BIT_FLAG_UNSUPPORTED_MASK) != 0) {
      throw new IOException("unsupported zip: general purpose bit flag=0x"
          + Integer.toHexString(bitFlag));
    }

    int compressionMethod = source.readShortLe() & 0xffff;
    int dosLastModifiedTime = source.readShortLe() & 0xffff;
    int dosLastModifiedDate = source.readShortLe() & 0xffff;

    // These are 32-bit values in the file, but 64-bit fields in this object.
    long crc = source.readIntLe() & 0xffffffffL;
    long compressedSize = source.readIntLe() & 0xffffffffL;
    long size = source.readIntLe() & 0xffffffffL;
    int nameSize = source.readShortLe() & 0xffff;
    int extraSize = source.readShortLe() & 0xffff;
    int commentByteCount = source.readShortLe() & 0xffff;

    source.skip(8); // disk number start (2) + internal attributes (2) + external attributes (4).
    long offset = source.readIntLe() & 0xffffffffL;
    String name = source.readUtf8((long) nameSize);
    if (name.indexOf('\u0000') != -1) {
      throw new IOException("bad zip: filename contains 0x00");
    }

    final long requiredZip64ExtraSize =
        (size == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE ? 8 : 0)
            + (compressedSize == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE ? 8 : 0)
            + (offset == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE ? 8 : 0);

    // The zip64 extras rewrite the sentinel sizes above; a mutable holder keeps them writable
    // from the extra-field callback.
    long[] sizes = new long[] {size, compressedSize, offset};
    Long[] ntfsFiletimes = new Long[] {null, null, null};
    boolean[] hasZip64Extra = new boolean[] {false};

    readExtra(source, extraSize, (headerId, dataSize) -> {
      switch (headerId) {
        case HEADER_ID_ZIP64_EXTENDED_INFO: {
          if (hasZip64Extra[0]) {
            throw new IOException("bad zip: zip64 extra repeated");
          }
          hasZip64Extra[0] = true;

          if (dataSize < requiredZip64ExtraSize) {
            throw new IOException("bad zip: zip64 extra too short");
          }

          // Read each field if it has a sentinel value in the regular header.
          if (sizes[0] == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) {
            sizes[0] = source.readLongLe();
          }
          if (sizes[1] == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) {
            sizes[1] = source.readLongLe();
          } else {
            sizes[1] = 0L;
          }
          if (sizes[2] == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) {
            sizes[2] = source.readLongLe();
          } else {
            sizes[2] = 0L;
          }
          break;
        }

        case HEADER_ID_NTFS_EXTRA: {
          if (dataSize < 4L) {
            throw new IOException("bad zip: NTFS extra too short");
          }
          source.skip(4L);

          // Reads the NTFS extra metadata. This metadata recursively does a tag and length scheme
          // inside of ZIP extras' own tag and length scheme. So we do readExtra() again.
          readExtra(source, (int) (dataSize - 4L), (attributeId, attributeSize) -> {
            if (attributeId == 0x1) {
              if (ntfsFiletimes[0] != null) {
                throw new IOException("bad zip: NTFS extra attribute tag 0x0001 repeated");
              }

              if (attributeSize != 24L) {
                throw new IOException("bad zip: NTFS extra attribute tag 0x0001 size != 24");
              }

              ntfsFiletimes[0] = source.readLongLe();
              ntfsFiletimes[1] = source.readLongLe();
              ntfsFiletimes[2] = source.readLongLe();
            }
          });
          break;
        }

        default:
          break;
      }
    });

    if (requiredZip64ExtraSize > 0L && !hasZip64Extra[0]) {
      throw new IOException("bad zip: zip64 extra required but absent");
    }

    String comment = source.readUtf8((long) commentByteCount);
    Path canonicalPath = Path.get("/").resolve(name);
    boolean isDirectory = name.endsWith("/");

    return new ZipEntry(
        canonicalPath,
        isDirectory,
        comment,
        crc,
        sizes[1],
        sizes[0],
        compressionMethod,
        sizes[2],
        dosLastModifiedDate,
        dosLastModifiedTime,
        ntfsFiletimes[0],
        ntfsFiletimes[1],
        ntfsFiletimes[2],
        -1,
        -1,
        -1);
  }

  private static EocdRecord readEocdRecord(BufferedSource source) throws IOException {
    int diskNumber = source.readShortLe() & 0xffff;
    int diskWithCentralDir = source.readShortLe() & 0xffff;
    long entryCount = source.readShortLe() & 0xffff;
    long totalEntryCount = source.readShortLe() & 0xffff;
    if (entryCount != totalEntryCount || diskNumber != 0 || diskWithCentralDir != 0) {
      throw new IOException("unsupported zip: spanned");
    }
    source.skip(4); // central directory size.
    long centralDirectoryOffset = source.readIntLe() & 0xffffffffL;
    int commentByteCount = source.readShortLe() & 0xffff;

    return new EocdRecord(entryCount, centralDirectoryOffset, commentByteCount);
  }

  private static EocdRecord readZip64EocdRecord(BufferedSource source, EocdRecord regularRecord)
      throws IOException {
    source.skip(12); // size of central directory record (8) + versions (4).
    int diskNumber = source.readIntLe();
    int diskWithCentralDirStart = source.readIntLe();
    long entryCount = source.readLongLe();
    long totalEntryCount = source.readLongLe();
    if (entryCount != totalEntryCount || diskNumber != 0 || diskWithCentralDirStart != 0) {
      throw new IOException("unsupported zip: spanned");
    }
    source.skip(8); // central directory size.
    long centralDirectoryOffset = source.readLongLe();

    return new EocdRecord(entryCount, centralDirectoryOffset, regularRecord.commentByteCount);
  }

  /**
   * Read a sequence of 0 or more extra fields. Each field has this structure:
   *
   * <ul>
   *   <li>2-byte header ID
   *   <li>2-byte data size
   *   <li>variable-byte data value
   * </ul>
   *
   * <p>This reads each extra field and calls {@code block} for each. The parameters are the header
   * ID and data size. It is an error for {@code block} to process more bytes than the data size.
   */
  private interface ExtraBlock {
    void accept(int headerId, long dataSize) throws IOException;
  }

  private static void readExtra(BufferedSource source, int extraSize, ExtraBlock block)
      throws IOException {
    long remaining = (long) extraSize;
    while (remaining != 0L) {
      if (remaining < 4) {
        throw new IOException("bad zip: truncated header in extra field");
      }
      int headerId = source.readShortLe() & 0xffff;
      long dataSize = source.readShortLe() & 0xffff;
      remaining -= 4;
      if (remaining < dataSize) {
        throw new IOException("bad zip: truncated value in extra field");
      }
      source.require(dataSize);
      long sizeBefore = source.buffer().size;
      block.accept(headerId, dataSize);
      long fieldRemaining = dataSize + source.buffer().size - sizeBefore;
      if (fieldRemaining < 0) {
        throw new IOException("unsupported zip: too many bytes processed for " + headerId);
      } else if (fieldRemaining > 0) {
        source.buffer().skip(fieldRemaining);
      }
      remaining -= dataSize;
    }
  }

  static void skipLocalHeader(BufferedSource source) throws IOException {
    readOrSkipLocalHeader(source, null);
  }

  static ZipEntry readLocalHeader(BufferedSource source, ZipEntry centralDirectoryZipEntry)
      throws IOException {
    return readOrSkipLocalHeader(source, centralDirectoryZipEntry);
  }

  /**
   * If {@code centralDirectoryZipEntry} is null this will return null. Otherwise, it will return a
   * new entry which unions {@code centralDirectoryZipEntry} with information from the local header.
   */
  private static ZipEntry readOrSkipLocalHeader(
      BufferedSource source, ZipEntry centralDirectoryZipEntry) throws IOException {
    int signature = source.readIntLe();
    if (signature != LOCAL_FILE_HEADER_SIGNATURE) {
      throw new IOException("bad zip: expected 0x"
          + Integer.toHexString(LOCAL_FILE_HEADER_SIGNATURE)
          + " but was 0x" + Integer.toHexString(signature));
    }
    source.skip(2); // version to extract.
    int bitFlag = source.readShortLe() & 0xffff;
    if ((bitFlag & BIT_FLAG_UNSUPPORTED_MASK) != 0) {
      throw new IOException("unsupported zip: general purpose bit flag=0x"
          + Integer.toHexString(bitFlag));
    }
    source.skip(18); // compression method (2) + time+date (4) + crc32 (4) + sizes (8).
    long fileNameLength = source.readShortLe() & 0xffff;
    int extraSize = source.readShortLe() & 0xffff;
    source.skip(fileNameLength);

    if (centralDirectoryZipEntry == null) {
      source.skip((long) extraSize);
      return null;
    }

    int[] extendedSeconds = new int[] {-1, -1, -1};

    readExtra(source, extraSize, (headerId, dataSize) -> {
      if (headerId == HEADER_ID_EXTENDED_TIMESTAMP) {
        if (dataSize < 1) {
          throw new IOException("bad zip: extended timestamp extra too short");
        }
        int flags = source.readByte() & 0xff;

        boolean hasLastModifiedAtMillis = (flags & 0x1) == 0x1;
        boolean hasLastAccessedAtMillis = (flags & 0x2) == 0x2;
        boolean hasCreatedAtMillis = (flags & 0x4) == 0x4;
        long requiredSize = 1L;
        if (hasLastModifiedAtMillis) {
          requiredSize += 4L;
        }
        if (hasLastAccessedAtMillis) {
          requiredSize += 4L;
        }
        if (hasCreatedAtMillis) {
          requiredSize += 4L;
        }
        if (dataSize < requiredSize) {
          throw new IOException("bad zip: extended timestamp extra too short");
        }

        if (hasLastModifiedAtMillis) {
          extendedSeconds[0] = source.readIntLe();
        }
        if (hasLastAccessedAtMillis) {
          extendedSeconds[1] = source.readIntLe();
        }
        if (hasCreatedAtMillis) {
          extendedSeconds[2] = source.readIntLe();
        }
      }
    });

    return centralDirectoryZipEntry.copy(
        extendedSeconds[0], extendedSeconds[1], extendedSeconds[2]);
  }

  /**
   * Converts from the Microsoft {@code filetime} format to the Java epoch millis format.
   *
   * <p>Filetime's unit is 100 nanoseconds, and 0 is 1601-01-01T00:00:00Z. Java epoch millis' unit
   * is 1 millisecond, and 0 is 1970-01-01T00:00:00Z.
   */
  static Long filetimeToEpochMillis(Long filetime) {
    // There's 11,644,473,600,000 milliseconds between 1601-01-01T00:00:00Z and 1970-01-01T00:00:00Z.
    return filetime / 10_000 - 11_644_473_600_000L;
  }

  /**
   * Converts a 32-bit DOS date+time to milliseconds since epoch. Note that this function interprets
   * a value with no time zone as a value with the local time zone.
   */
  static Long dosDateTimeToEpochMillis(int date, int time) {
    if (time == -1) {
      return null;
    }

    Calendar calendar = new GregorianCalendar(TimeZone.getDefault());
    calendar.set(Calendar.YEAR, 1980 + (date >> 9 & 0x7f));
    calendar.set(Calendar.MONTH, (date >> 5 & 0xf) - 1);
    calendar.set(Calendar.DAY_OF_MONTH, date & 0x1f);
    calendar.set(Calendar.HOUR_OF_DAY, time >> 11 & 0x1f);
    calendar.set(Calendar.MINUTE, time >> 5 & 0x3f);
    calendar.set(Calendar.SECOND, (time & 0x1f) << 1);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTimeInMillis();
  }

  private static final class EocdRecord {
    final long entryCount;
    final long centralDirectoryOffset;
    final int commentByteCount;

    EocdRecord(long entryCount, long centralDirectoryOffset, int commentByteCount) {
      this.entryCount = entryCount;
      this.centralDirectoryOffset = centralDirectoryOffset;
      this.commentByteCount = commentByteCount;
    }
  }
}
