/*
 * Copyright (C) 2026 the antiwire authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

/**
 * Metadata of a file or directory, as returned by {@link FileSystem#metadataOrNull}. The API
 * shape follows okio 3's FileMetadata (Apache 2.0, Square); the implementation is original to
 * this port. Fields a caller does not ask about may be null; wire's loader reads
 * {@link #isDirectory}, {@link #isRegularFile} and {@link #symlinkTarget}.
 */
public final class FileMetadata {
  public final Boolean isRegularFile;
  public final Boolean isDirectory;
  public final Long byteSize;
  public final Path symlinkTarget;

  public FileMetadata(
      Boolean isRegularFile, Boolean isDirectory, Long byteSize, Path symlinkTarget) {
    this.isRegularFile = isRegularFile;
    this.isDirectory = isDirectory;
    this.byteSize = byteSize;
    this.symlinkTarget = symlinkTarget;
  }
}
