/*
 * Translated from okio/src/jvmMain/kotlin/okio/JvmFileHandle.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under
 * the notices below. Package-private: upstream internal.
 *
 * Copyright (C) 2021 Square, Inc.
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

import java.io.IOException;
import java.io.RandomAccessFile;

final class JvmFileHandle extends FileHandle {

  private final RandomAccessFile randomAccessFile;

  JvmFileHandle(boolean readWrite, RandomAccessFile randomAccessFile) {
    super(readWrite);
    this.randomAccessFile = randomAccessFile;
  }

  @Override protected synchronized void protectedResize(long size) throws IOException {
    long currentSize = size();
    long delta = size - currentSize;
    if (delta > 0) {
      byte[] zeros = new byte[(int) delta];
      protectedWrite(currentSize, zeros, 0, zeros.length);
    } else {
      randomAccessFile.setLength(size);
    }
  }

  @Override protected synchronized long protectedSize() throws IOException {
    return randomAccessFile.length();
  }

  @Override protected synchronized int protectedRead(
      long fileOffset, byte[] array, int arrayOffset, int byteCount) throws IOException {
    randomAccessFile.seek(fileOffset);
    int bytesRead = 0;
    while (bytesRead < byteCount) {
      int readResult = randomAccessFile.read(array, arrayOffset, byteCount - bytesRead);
      if (readResult == -1) {
        if (bytesRead == 0) {
          return -1;
        }
        break;
      }
      bytesRead += readResult;
    }
    return bytesRead;
  }

  @Override protected synchronized void protectedWrite(
      long fileOffset, byte[] array, int arrayOffset, int byteCount) throws IOException {
    randomAccessFile.seek(fileOffset);
    randomAccessFile.write(array, arrayOffset, byteCount);
  }

  @Override protected synchronized void protectedFlush() throws IOException {
    randomAccessFile.getFD().sync();
  }

  @Override protected synchronized void protectedClose() throws IOException {
    randomAccessFile.close();
  }
}
