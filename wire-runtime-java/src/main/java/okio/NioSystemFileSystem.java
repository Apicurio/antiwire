/*
 * Translated from okio/src/jvmMain/kotlin/okio/NioSystemFileSystem.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under
 * the notices below. Package-private: upstream internal open class.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

final class NioSystemFileSystem extends JvmSystemFileSystem {

  @Override public FileMetadata metadataOrNull(Path path) throws IOException {
    return metadataOrNull(path.toNioPath());
  }

  private FileMetadata metadataOrNull(java.nio.file.Path nioPath) throws IOException {
    BasicFileAttributes attributes;
    try {
      attributes = Files.readAttributes(
          nioPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    } catch (NoSuchFileException e) {
      return null;
    } catch (FileSystemException e) {
      return null;
    }

    java.nio.file.Path symlinkTarget = attributes.isSymbolicLink()
        ? Files.readSymbolicLink(nioPath)
        : null;

    return new FileMetadata(
        attributes.isRegularFile(),
        attributes.isDirectory(),
        symlinkTarget != null ? Path.get(symlinkTarget) : null,
        attributes.size(),
        zeroToNull(attributes.creationTime()),
        zeroToNull(attributes.lastModifiedTime()),
        zeroToNull(attributes.lastAccessTime()));
  }

  private static Long zeroToNull(FileTime fileTime) {
    if (fileTime == null) {
      return null;
    }
    long millis = fileTime.toMillis();
    return millis != 0L ? millis : null;
  }

  @Override public void atomicMove(Path source, Path target) throws IOException {
    try {
      Files.move(source.toNioPath(), target.toNioPath(),
          StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (NoSuchFileException e) {
      throw new FileNotFoundException(e.getMessage());
    } catch (UnsupportedOperationException e) {
      throw new IOException("atomic move not supported");
    }
  }

  @Override public void createSymlink(Path source, Path target) throws IOException {
    Files.createSymbolicLink(source.toNioPath(), target.toNioPath());
  }

  @Override public String toString() {
    return "NioSystemFileSystem";
  }
}
