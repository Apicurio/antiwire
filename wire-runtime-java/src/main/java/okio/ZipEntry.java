/*
 * Translated from okio/src/zlibMain/kotlin/okio/internal/ZipEntry.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under
 * the notices below. Package-private in okio rather than okio.internal, matching the
 * visibility collapse of the port's ZIP stack.
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

import java.util.ArrayList;
import java.util.List;

final class ZipEntry {

  /** The path of this entry in the zip file system, like `/squareup/dinosaurs/dino.proto`. */
  final Path canonicalPath;

  /** True if this entry is a directory. When encoded directory entries' names end with `/`. */
  final boolean isDirectory;

  /** The comment on this entry. Empty if there is no comment. */
  final String comment;

  /** The CRC32 of the uncompressed data, or -1 if not set. */
  final long crc;

  /** The compressed size in bytes, or -1 if unknown. */
  final long compressedSize;

  /** The uncompressed size in bytes, or -1 if unknown. */
  final long size;

  /** Either DEFLATED or STORED. */
  final int compressionMethod;

  /** The offset of this entry's local header in the zip file, or -1 if unknown. */
  final long offset;

  final int dosLastModifiedAtDate;
  final int dosLastModifiedAtTime;

  /** Filetime is 100 nanoseconds since 1601-01-01T00:00:00Z, null if not set. */
  final Long ntfsLastModifiedAtFiletime;
  final Long ntfsLastAccessedAtFiletime;
  final Long ntfsCreatedAtFiletime;

  /** Unix timestamps, in seconds, null if not set. */
  final int extendedLastModifiedAtSeconds;
  final int extendedLastAccessedAtSeconds;
  final int extendedCreatedAtSeconds;

  final List<Path> children = new ArrayList<>();

  ZipEntry(
      Path canonicalPath,
      boolean isDirectory,
      String comment,
      long crc,
      long compressedSize,
      long size,
      int compressionMethod,
      long offset,
      int dosLastModifiedAtDate,
      int dosLastModifiedAtTime) {
    this(canonicalPath, isDirectory, comment, crc, compressedSize, size, compressionMethod, offset,
        dosLastModifiedAtDate, dosLastModifiedAtTime, null, null, null, -1, -1, -1);
  }

  ZipEntry(
      Path canonicalPath,
      boolean isDirectory,
      String comment,
      long crc,
      long compressedSize,
      long size,
      int compressionMethod,
      long offset,
      int dosLastModifiedAtDate,
      int dosLastModifiedAtTime,
      Long ntfsLastModifiedAtFiletime,
      Long ntfsLastAccessedAtFiletime,
      Long ntfsCreatedAtFiletime,
      int extendedLastModifiedAtSeconds,
      int extendedLastAccessedAtSeconds,
      int extendedCreatedAtSeconds) {
    this.canonicalPath = canonicalPath;
    this.isDirectory = isDirectory;
    this.comment = comment;
    this.crc = crc;
    this.compressedSize = compressedSize;
    this.size = size;
    this.compressionMethod = compressionMethod;
    this.offset = offset;
    this.dosLastModifiedAtDate = dosLastModifiedAtDate;
    this.dosLastModifiedAtTime = dosLastModifiedAtTime;
    this.ntfsLastModifiedAtFiletime = ntfsLastModifiedAtFiletime;
    this.ntfsLastAccessedAtFiletime = ntfsLastAccessedAtFiletime;
    this.ntfsCreatedAtFiletime = ntfsCreatedAtFiletime;
    this.extendedLastModifiedAtSeconds = extendedLastModifiedAtSeconds;
    this.extendedLastAccessedAtSeconds = extendedLastAccessedAtSeconds;
    this.extendedCreatedAtSeconds = extendedCreatedAtSeconds;
  }

  ZipEntry copy(
      int extendedLastModifiedAtSeconds,
      int extendedLastAccessedAtSeconds,
      int extendedCreatedAtSeconds) {
    return new ZipEntry(
        canonicalPath,
        isDirectory,
        comment,
        crc,
        compressedSize,
        size,
        compressionMethod,
        offset,
        dosLastModifiedAtDate,
        dosLastModifiedAtTime,
        ntfsLastModifiedAtFiletime,
        ntfsLastAccessedAtFiletime,
        ntfsCreatedAtFiletime,
        extendedLastModifiedAtSeconds,
        extendedLastAccessedAtSeconds,
        extendedCreatedAtSeconds);
  }

  Long lastAccessedAtMillis() {
    if (ntfsLastAccessedAtFiletime != null) {
      return ZipFiles.filetimeToEpochMillis(ntfsLastAccessedAtFiletime);
    }
    if (extendedLastAccessedAtSeconds != -1) {
      return ((long) extendedLastAccessedAtSeconds) * 1000L;
    }
    return null;
  }

  Long lastModifiedAtMillis() {
    if (ntfsLastModifiedAtFiletime != null) {
      return ZipFiles.filetimeToEpochMillis(ntfsLastModifiedAtFiletime);
    }
    if (extendedLastModifiedAtSeconds != -1) {
      return ((long) extendedLastModifiedAtSeconds) * 1000L;
    }
    if (dosLastModifiedAtTime != -1) {
      return ZipFiles.dosDateTimeToEpochMillis(dosLastModifiedAtDate, dosLastModifiedAtTime);
    }
    return null;
  }

  Long createdAtMillis() {
    if (ntfsCreatedAtFiletime != null) {
      return ZipFiles.filetimeToEpochMillis(ntfsCreatedAtFiletime);
    }
    if (extendedCreatedAtSeconds != -1) {
      return ((long) extendedCreatedAtSeconds) * 1000L;
    }
    return null;
  }
}
