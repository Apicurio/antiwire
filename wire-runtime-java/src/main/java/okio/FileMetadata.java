/*
 * Translated from okio/src/commonMain/kotlin/okio/FileMetadata.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under
 * the notices below. Translation note: the KClass-keyed extras map is dropped from the
 * M0 subset (no wire main or wire test use at the pin); the typed fields the wire-schema
 * loader and its tests observe are kept.
 *
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

/**
 * Description of a file or another object referenced by a path in a {@link FileSystem}.
 *
 * <p>Metadata is returned by means of an object because not all file systems have all the
 * features to fill it.
 */
public final class FileMetadata {

  /** True if this file is a container of bytes. If this is true, then {@code size} is non-null. */
  public final Boolean isRegularFile;

  /**
   * True if this file is a container of other files. This is a directory or a folder. If this is
   * true then {@code byteCount} is null.
   */
  public final Boolean isDirectory;

  /** The target of a symbolic link. Null if this is not a symbolic link. */
  public final Path symlinkTarget;

  /**
   * The size of the file in bytes. This is null if the path is not a container of bytes, like a
   * directory.
   */
  public final Long size;

  /** The system time of the host computer when this file was created. Null if unknown. */
  public final Long createdAtMillis;

  /** The system time of the host computer when this file was most recently written. */
  public final Long lastModifiedAtMillis;

  /** The system time of the host computer when this file was most recently read or executed. */
  public final Long lastAccessedAtMillis;

  public FileMetadata(
      Boolean isRegularFile,
      Boolean isDirectory,
      Path symlinkTarget,
      Long size,
      Long createdAtMillis,
      Long lastModifiedAtMillis,
      Long lastAccessedAtMillis) {
    this.isRegularFile = isRegularFile;
    this.isDirectory = isDirectory;
    this.symlinkTarget = symlinkTarget;
    this.size = size;
    this.createdAtMillis = createdAtMillis;
    this.lastModifiedAtMillis = lastModifiedAtMillis;
    this.lastAccessedAtMillis = lastAccessedAtMillis;
  }

  @Override public String toString() {
    StringBuilder fields = new StringBuilder("FileMetadata(");
    String separator = "";
    if (Boolean.TRUE.equals(isRegularFile)) {
      fields.append(separator).append("isRegularFile");
      separator = ", ";
    }
    if (Boolean.TRUE.equals(isDirectory)) {
      fields.append(separator).append("isDirectory");
      separator = ", ";
    }
    if (size != null) {
      fields.append(separator).append("byteCount=").append(size);
      separator = ", ";
    }
    if (createdAtMillis != null) {
      fields.append(separator).append("createdAt=").append(createdAtMillis);
      separator = ", ";
    }
    if (lastModifiedAtMillis != null) {
      fields.append(separator).append("lastModifiedAt=").append(lastModifiedAtMillis);
      separator = ", ";
    }
    if (lastAccessedAtMillis != null) {
      fields.append(separator).append("lastAccessedAt=").append(lastAccessedAtMillis);
    }
    return fields.append(")").toString();
  }
}
