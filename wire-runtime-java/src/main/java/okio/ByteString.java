/*
 * Translated from okio/src/jvmMain/kotlin/okio/ByteString.kt (with the common helpers of
 * okio/src/commonMain/kotlin/okio/internal/ByteString.kt inlined) at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 * Derivative work under the notices below.
 *
 * Translation notes for the M0 subset (docs/decisions.md OPEN-1):
 *  - Excluded with scope justification, no wire main or wire test use at wire tag 7.1.0:
 *    md5/sha1/sha256/sha512/hmacSha1/hmacSha256/hmacSha512, InputStream.readByteString,
 *    and the Kotlin-error-level deprecated shims. SegmentedByteString overrides the
 *    digest()/hmac() hooks by throwing UnsupportedOperationException (they are absent
 *    from the base class here).
 *  - Kotlin companion extensions become static methods on this class: "s".encodeUtf8()
 *    is ByteString.encodeUtf8("s"), "s".decodeHex() is ByteString.decodeHex("s"),
 *    "s".decodeBase64() is ByteString.decodeBase64("s"), array.toByteString(o, n) is
 *    ByteString.of(array, o, n). This is the source compatibility mapping recorded in
 *    docs/decisions.md OPEN-1 for adapted Kotlin tests.
 *  - decodeBase64 returns null for invalid input, exactly like the Kotlin signature
 *    String.decodeBase64(): ByteString?.
 *  - codePointIndexToCharIndex scans UTF-8 bytes through the process2/3/4Utf8Bytes
 *    helpers below, a direct translation of the upstream Utf8.kt decoder: every malformed
 *    or rejected sequence yields the replacement code point exactly where upstream's
 *    processUtf8CodePoints does (overlong forms, partial surrogates, code points above
 *    the Unicode maximum, unexpected continuations, and unknown encodings), and the
 *    closure checks j++ == codePointCount before the replacement check, so the verdicts
 *    match the pinned artifact for every input.
 *
 * Copyright 2014 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * An immutable sequence of bytes.
 *
 * <p>Byte strings compare lexicographically as a sequence of <b>unsigned</b> bytes. That is, the
 * byte string {@code ff} sorts after {@code 00}. This is counter to the sort order of the
 * corresponding bytes, where {@code -1} sorts before {@code 0}.
 *
 * <p><b>Full disclosure:</b> this class provides untrusted input and output streams with raw access
 * to the underlying byte array. A hostile stream implementation could keep a reference to the
 * mutable byte string, violating the immutable guarantee of this class. For this reason a byte
 * string's immutability guarantee cannot be relied upon for security in applets and other
 * environments that run both trusted and untrusted code in the same process.
 */
public class ByteString implements Serializable, Comparable<ByteString> {

  private static final long serialVersionUID = 1L;

  private static final char[] HEX_DIGIT_CHARS = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
  };

  /** A singleton empty {@code ByteString}. */
  public static final ByteString EMPTY = new ByteString(new byte[0]);

  final byte[] data;

  transient int hashCode; // Lazily computed; 0 if unknown.
  transient String utf8; // Lazily computed.

  // Trusted internal constructor doesn't clone data.
  ByteString(byte[] data) {
    this.data = data;
  }

  /**
   * Returns a new byte string containing a clone of the bytes of {@code data}. Declared as a
   * vararg so both the Kotlin spread form ({@code ByteString.of(*array)}) and the Java element
   * form ({@code ByteString.of((byte) 1, (byte) 2)}) resolve, matching the upstream JVM surface.
   */
  public static ByteString of(byte... data) {
    return of(data, 0, data.length);
  }

  /**
   * Returns a new {@code ByteString} containing a copy of {@code byteCount} bytes of
   * {@code data} starting at {@code offset}.
   *
   * <p>Upstream: {@code data.toByteString(offset, byteCount)} and {@code ByteString.of(vararg)}.
   */
  public static ByteString of(byte[] data, int offset, int byteCount) {
    Util.checkOffsetAndCount(data.length, offset, byteCount);
    byte[] copy = new byte[byteCount];
    System.arraycopy(data, offset, copy, 0, byteCount);
    return new ByteString(copy);
  }

  /** Returns a new {@code ByteString} containing a copy of the remaining bytes of {@code data}. */
  public static ByteString of(ByteBuffer data) {
    byte[] copy = new byte[data.remaining()];
    data.get(copy);
    return new ByteString(copy);
  }

  /** Returns a new byte string containing the {@code UTF-8} bytes of {@code s}. */
  public static ByteString encodeUtf8(String s) {
    ByteString byteString = new ByteString(Utf8.bytes(s));
    byteString.utf8 = s;
    return byteString;
  }

  /** Returns a new {@code ByteString} containing the {@code charset}-encoded bytes of {@code s}. */
  public static ByteString encodeString(String s, Charset charset) {
    return new ByteString(s.getBytes(charset));
  }

  /**
   * Decodes the Base64-encoded bytes and returns their value as a byte string. Returns null if
   * this is not a Base64-encoded sequence of bytes.
   */
  public static ByteString decodeBase64(String s) {
    byte[] decoded = Base64.decodeBase64ToArray(s);
    return decoded != null ? new ByteString(decoded) : null;
  }

  /** Decodes the hex-encoded bytes and returns their value as a byte string. */
  public static ByteString decodeHex(String s) {
    if (s.length() % 2 != 0) {
      throw new IllegalArgumentException("Unexpected hex string: " + s);
    }
    byte[] result = new byte[s.length() / 2];
    for (int i = 0; i < result.length; i++) {
      int d1 = decodeHexDigit(s.charAt(i * 2));
      int d2 = decodeHexDigit(s.charAt(i * 2 + 1));
      result[i] = (byte) ((d1 << 4) + d2);
    }
    return new ByteString(result);
  }

  private static int decodeHexDigit(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    }
    throw new IllegalArgumentException("Unexpected hex digit: " + c);
  }

  /** Constructs a new {@code String} by decoding the bytes as {@code UTF-8}. */
  public String utf8() {
    String result = utf8;
    if (result == null) {
      // We don't care if we double-allocate in racy code.
      result = Utf8.string(internalArray());
      utf8 = result;
    }
    return result;
  }

  /** Constructs a new {@code String} by decoding the bytes using {@code charset}. */
  public String string(Charset charset) {
    return new String(data, charset);
  }

  /**
   * Returns this byte string encoded as <a href="https://www.ietf.org/rfc/rfc2045.txt">Base64</a>.
   * In violation of the RFC, the returned string does not wrap lines at 76 columns.
   */
  public String base64() {
    return base64(true);
  }

  public String base64(boolean includePadding) {
    return Base64.encodeBase64(data, includePadding, Base64.BASE64_MAP);
  }

  /** Returns this byte string encoded as <a href="https://www.ietf.org/rfc/rfc4648.txt">URL-safe Base64</a>. */
  public String base64Url() {
    return base64Url(true);
  }

  public String base64Url(boolean includePadding) {
    return Base64.encodeBase64(data, includePadding, Base64.BASE64_URL_SAFE_MAP);
  }

  /** Returns this byte string encoded in hexadecimal. */
  public String hex() {
    char[] result = new char[data.length * 2];
    int c = 0;
    for (byte b : data) {
      result[c++] = HEX_DIGIT_CHARS[(b >> 4) & 0xf];
      result[c++] = HEX_DIGIT_CHARS[b & 0xf];
    }
    return new String(result);
  }

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'A' through 'Z' replaced
   * with the corresponding byte in 'a' through 'z'. Returns this byte string if it contains no
   * bytes in 'A' through 'Z'.
   */
  public ByteString toAsciiLowercase() {
    // Search for an uppercase character. If we don't find one, return this.
    int i = 0;
    while (i < data.length) {
      byte c = data[i];
      if (c < 'A' || c > 'Z') {
        i++;
        continue;
      }

      // This string needs to be lowercased. Create and return a new byte string.
      byte[] lowercase = data.clone();
      lowercase[i++] = (byte) (c - ('A' - 'a'));
      while (i < lowercase.length) {
        c = lowercase[i];
        if (c < 'A' || c > 'Z') {
          i++;
          continue;
        }
        lowercase[i] = (byte) (c - ('A' - 'a'));
        i++;
      }
      return new ByteString(lowercase);
    }
    return this;
  }

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'a' through 'z' replaced
   * with the corresponding byte in 'A' through 'Z'. Returns this byte string if it contains no
   * bytes in 'a' through 'z'.
   */
  public ByteString toAsciiUppercase() {
    // Search for a lowercase character. If we don't find one, return this.
    int i = 0;
    while (i < data.length) {
      byte c = data[i];
      if (c < 'a' || c > 'z') {
        i++;
        continue;
      }

      // This string needs to be uppercased. Create and return a new byte string.
      byte[] uppercase = data.clone();
      uppercase[i++] = (byte) (c - ('a' - 'A'));
      while (i < uppercase.length) {
        c = uppercase[i];
        if (c < 'a' || c > 'z') {
          i++;
          continue;
        }
        uppercase[i] = (byte) (c - ('a' - 'A'));
        i++;
      }
      return new ByteString(uppercase);
    }
    return this;
  }

  /**
   * Returns a byte string that is a substring of this byte string, beginning at the specified
   * {@code beginIndex} and ending at the specified {@code endIndex}. Returns this byte string if
   * {@code beginIndex} is 0 and {@code endIndex} is the length of this byte string.
   */
  public ByteString substring(int beginIndex) {
    return substring(beginIndex, data.length);
  }

  public ByteString substring(int beginIndex, int endIndex) {
    if (beginIndex < 0) {
      throw new IllegalArgumentException("beginIndex < 0");
    }
    if (endIndex > data.length) {
      throw new IllegalArgumentException("endIndex > length(" + data.length + ")");
    }

    int subLen = endIndex - beginIndex;
    if (subLen < 0) {
      throw new IllegalArgumentException("endIndex < beginIndex");
    }

    if (beginIndex == 0 && endIndex == data.length) {
      return this;
    }
    byte[] copy = new byte[subLen];
    System.arraycopy(data, beginIndex, copy, 0, subLen);
    return new ByteString(copy);
  }

  /** Returns the byte at {@code index}. Named {@code get} so Kotlin callers can use {@code byteString[index]}. */
  public byte get(int index) {
    return data[index];
  }

  /**
   * Returns the byte at {@code index}. Upstream exposes this JVM name via JvmName on the
   * operator get; the port keeps both spellings for Java and Kotlin source compatibility.
   */
  public byte getByte(int index) {
    return get(index);
  }

  /** Returns the number of bytes in this ByteString. */
  public int size() {
    return data.length;
  }

  /**
   * Returns the number of bytes in this ByteString. Upstream declares this internal JVM
   * method to back the Kotlin {@code size} property; the port keeps it public so Kotlin
   * consumers can use the property spelling against the Java class.
   */
  public int getSize() {
    return size();
  }

  /** Returns a byte array containing a copy of the bytes in this {@code ByteString}. */
  public byte[] toByteArray() {
    return data.clone();
  }

  /** Returns the bytes of this string without a defensive copy. Do not mutate! */
  byte[] internalArray() {
    return data;
  }

  /** Returns a {@code ByteBuffer} view of the bytes in this {@code ByteString}. */
  public ByteBuffer asByteBuffer() {
    return ByteBuffer.wrap(data).asReadOnlyBuffer();
  }

  /** Writes the contents of this byte string to {@code out}. */
  public void write(OutputStream out) throws IOException {
    out.write(data);
  }

  /** Writes the contents of this byte string to {@code buffer}. */
  void write(Buffer buffer, int offset, int byteCount) {
    buffer.write(data, offset, byteCount);
  }

  /**
   * Returns true if the bytes of this in {@code [offset..offset+byteCount)} equal the bytes of
   * {@code other} in {@code [otherOffset..otherOffset+byteCount)}. Returns false if either range
   * is out of bounds.
   */
  public boolean rangeEquals(int offset, ByteString other, int otherOffset, int byteCount) {
    return other.rangeEquals(otherOffset, data, offset, byteCount);
  }

  /**
   * Returns true if the bytes of this in {@code [offset..offset+byteCount)} equal the bytes of
   * {@code other} in {@code [otherOffset..otherOffset+byteCount)}. Returns false if either range
   * is out of bounds.
   */
  public boolean rangeEquals(int offset, byte[] other, int otherOffset, int byteCount) {
    return offset >= 0 && offset <= data.length - byteCount
        && otherOffset >= 0 && otherOffset <= other.length - byteCount
        && Util.arrayRangeEquals(data, offset, other, otherOffset, byteCount);
  }

  /**
   * Copies bytes of this in {@code [offset..offset+byteCount]} to other in
   * {@code [targetOffset..targetOffset+byteCount]}.
   *
   * @throws IndexOutOfBoundsException if either range is out of bounds.
   */
  public void copyInto(int offset, byte[] target, int targetOffset, int byteCount) {
    System.arraycopy(data, offset, target, targetOffset, byteCount);
  }

  public boolean startsWith(ByteString prefix) {
    return rangeEquals(0, prefix, 0, prefix.data.length);
  }

  public boolean startsWith(byte[] prefix) {
    return rangeEquals(0, prefix, 0, prefix.length);
  }

  public boolean endsWith(ByteString suffix) {
    return rangeEquals(data.length - suffix.data.length, suffix, 0, suffix.data.length);
  }

  public boolean endsWith(byte[] suffix) {
    return rangeEquals(data.length - suffix.length, suffix, 0, suffix.length);
  }

  public int indexOf(ByteString other) {
    return indexOf(other.internalArray(), 0);
  }

  public int indexOf(ByteString other, int fromIndex) {
    return indexOf(other.internalArray(), fromIndex);
  }

  public int indexOf(byte[] other) {
    return indexOf(other, 0);
  }

  public int indexOf(byte[] other, int fromIndex) {
    int limit = data.length - other.length;
    for (int i = Math.max(fromIndex, 0); i <= limit; i++) {
      if (Util.arrayRangeEquals(data, i, other, 0, other.length)) {
        return i;
      }
    }
    return -1;
  }

  public int lastIndexOf(ByteString other) {
    return lastIndexOf(other.internalArray(), data.length);
  }

  public int lastIndexOf(ByteString other, int fromIndex) {
    return lastIndexOf(other.internalArray(), fromIndex);
  }

  public int lastIndexOf(byte[] other) {
    return lastIndexOf(other, data.length);
  }

  public int lastIndexOf(byte[] other, int fromIndex) {
    int limit = data.length - other.length;
    for (int i = Math.min(fromIndex, limit); i >= 0; i--) {
      if (Util.arrayRangeEquals(data, i, other, 0, other.length)) {
        return i;
      }
    }
    return -1;
  }

  @Override public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (other instanceof ByteString) {
      ByteString that = (ByteString) other;
      return that.size() == data.length
          && that.rangeEquals(0, data, 0, data.length);
    }
    return false;
  }

  /**
   * Returns true if the bytes of this equal the bytes of {@code other}. If {@code constantTime} is
   * true this always inspects every byte and does not short-circuit on the first mismatch, so its
   * running time does not depend on where the byte strings differ. Use that for timing-safe
   * comparison of secrets like hashes or message authentication codes. If {@code constantTime} is
   * false this behaves like {@link #equals(Object)} and may return as soon as a mismatch is found.
   */
  public final boolean equals(ByteString other, boolean constantTime) {
    if (!constantTime) {
      return equals(other);
    }
    if (other == this) {
      return true;
    }
    if (other == null || other.size() != data.length) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < data.length; i++) {
      result |= data[i] ^ other.get(i);
    }
    return result == 0;
  }

  @Override public int hashCode() {
    int result = hashCode;
    if (result != 0) {
      return result;
    }
    result = Arrays.hashCode(data);
    hashCode = result;
    return result;
  }

  @Override public int compareTo(ByteString other) {
    int sizeA = data.length;
    int sizeB = other.data.length;
    int i = 0;
    int size = Math.min(sizeA, sizeB);
    while (i < size) {
      int byteA = data[i] & 0xff;
      int byteB = other.data[i] & 0xff;
      if (byteA == byteB) {
        i++;
        continue;
      }
      return byteA < byteB ? -1 : 1;
    }
    if (sizeA == sizeB) {
      return 0;
    }
    return sizeA < sizeB ? -1 : 1;
  }

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically
   * this is a string like {@code [text=Hello]} or {@code [hex=0000ffff]}.
   */
  @Override public String toString() {
    if (data.length == 0) {
      return "[size=0]";
    }

    int i = codePointIndexToCharIndex(data, 64);
    if (i == -1) {
      if (data.length <= 64) {
        return "[hex=" + hex() + "]";
      }
      return "[size=" + data.length + " hex=" + substring(0, 64).hex() + "…]";
    }

    String text = utf8();
    String safeText = text.substring(0, i)
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
    if (i < text.length()) {
      return "[size=" + data.length + " text=" + safeText + "…]";
    }
    return "[text=" + safeText + "]";
  }

  private static int codePointIndexToCharIndex(byte[] s, int codePointCount) {
    int charCount = 0;
    int j = 0;
    int index = 0;
    int[] codePoint = new int[1];
    while (index < s.length) {
      int b0 = s[index];
      if (b0 >= 0) {
        // 0b0xxxxxxx. Upstream's inner ASCII loop only batches the yields; the closure
        // below still runs once per byte, so a single step is equivalent.
        codePoint[0] = b0;
        index++;
      } else if (b0 >> 5 == -2) {
        // 0b110xxxxx.
        index += process2Utf8Bytes(s, index, s.length, codePoint);
      } else if (b0 >> 4 == -2) {
        // 0b1110xxxx.
        index += process3Utf8Bytes(s, index, s.length, codePoint);
      } else if (b0 >> 3 == -2) {
        // 0b11110xxx.
        index += process4Utf8Bytes(s, index, s.length, codePoint);
      } else {
        // 0b10xxxxxx - unexpected continuation, or 0b111111xx - unknown encoding.
        codePoint[0] = Utf8.REPLACEMENT_CODE_POINT;
        index++;
      }

      // The closure checks j++ == codePointCount BEFORE the replacement check, per
      // upstream processUtf8CodePoints (Utf8.kt), so a replacement at the cut index still
      // yields the text rendering.
      if (j++ == codePointCount) {
        return charCount;
      }
      int c = codePoint[0];
      if ((c != '\n' && c != '\r' && Utf8.isIsoControl(c)) || c == Utf8.REPLACEMENT_CODE_POINT) {
        return -1;
      }
      charCount += c < 0x10000 ? 1 : 2;
    }
    return charCount;
  }

  // Masks used to remove byte headers from multi-byte encoded UTF-8 characters; the
  // constants and helpers below mirror Utf8.kt process2/3/4Utf8Bytes, with the yield
  // delivered through the single-element holder so exactly one code point (the decoded
  // value or the replacement) is produced per call.

  private static final int MASK_2BYTES = 0x0f80;
  private static final int MASK_3BYTES = -0x01e080;
  private static final int MASK_4BYTES = 0x381f80;

  private static boolean isUtf8Continuation(byte b) {
    // 0b10xxxxxx.
    return (b & 0xc0) == 0x80;
  }

  private static int process2Utf8Bytes(byte[] s, int beginIndex, int endIndex, int[] codePointOut) {
    if (endIndex <= beginIndex + 1) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT;
      return 1; // Only 1 byte remaining - underflow.
    }

    byte b0 = s[beginIndex];
    byte b1 = s[beginIndex + 1];
    if (!isUtf8Continuation(b1)) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT;
      return 1;
    }

    int codePoint = MASK_2BYTES ^ b1 ^ (b0 << 6);
    if (codePoint < 0x80) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT; // Reject overlong code points.
    } else {
      codePointOut[0] = codePoint;
    }
    return 2;
  }

  private static int process3Utf8Bytes(byte[] s, int beginIndex, int endIndex, int[] codePointOut) {
    if (endIndex <= beginIndex + 2) {
      // At least 2 bytes remaining.
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT;
      if (endIndex <= beginIndex + 1 || !isUtf8Continuation(s[beginIndex + 1])) {
        // Only 1 byte remaining - underflow, or 2nd byte is not a continuation - malformed.
        return 1;
      } else {
        // Only 2 bytes remaining - underflow.
        return 2;
      }
    }

    byte b0 = s[beginIndex];
    byte b1 = s[beginIndex + 1];
    if (!isUtf8Continuation(b1)) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT;
      return 1;
    }
    byte b2 = s[beginIndex + 2];
    if (!isUtf8Continuation(b2)) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT;
      return 2;
    }

    int codePoint = MASK_3BYTES ^ b2 ^ (b1 << 6) ^ (b0 << 12);
    if (codePoint < 0x800) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT; // Reject overlong code points.
    } else if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT; // Reject partial surrogates.
    } else {
      codePointOut[0] = codePoint;
    }
    return 3;
  }

  private static int process4Utf8Bytes(byte[] s, int beginIndex, int endIndex, int[] codePointOut) {
    if (endIndex <= beginIndex + 3) {
      // At least 3 bytes remaining.
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT;
      if (endIndex <= beginIndex + 1 || !isUtf8Continuation(s[beginIndex + 1])) {
        // Only 1 byte remaining - underflow, or 2nd byte is not a continuation - malformed.
        return 1;
      } else if (endIndex <= beginIndex + 2 || !isUtf8Continuation(s[beginIndex + 2])) {
        // Only 2 bytes remaining - underflow, or 3rd byte is not a continuation - malformed.
        return 2;
      } else {
        // Only 3 bytes remaining - underflow.
        return 3;
      }
    }

    byte b0 = s[beginIndex];
    byte b1 = s[beginIndex + 1];
    if (!isUtf8Continuation(b1)) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT;
      return 1;
    }
    byte b2 = s[beginIndex + 2];
    if (!isUtf8Continuation(b2)) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT;
      return 2;
    }
    byte b3 = s[beginIndex + 3];
    if (!isUtf8Continuation(b3)) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT;
      return 3;
    }

    int codePoint = MASK_4BYTES ^ b3 ^ (b2 << 6) ^ (b1 << 12) ^ (b0 << 18);
    if (codePoint > 0x10ffff) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT; // Reject code points above the maximum.
    } else if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT; // Reject partial surrogates.
    } else if (codePoint < 0x10000) {
      codePointOut[0] = Utf8.REPLACEMENT_CODE_POINT; // Reject overlong code points.
    } else {
      codePointOut[0] = codePoint;
    }
    return 4;
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    int dataLength = in.readInt();
    ByteString byteString = readByteString(in, dataLength);
    try {
      java.lang.reflect.Field field = ByteString.class.getDeclaredField("data");
      field.setAccessible(true);
      field.set(this, byteString.data);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new InvalidObjectException("cannot restore ByteString data: " + e);
    }
  }

  private static ByteString readByteString(ObjectInputStream in, int byteCount) throws IOException {
    if (byteCount < 0) {
      throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    }
    byte[] result = new byte[byteCount];
    int offset = 0;
    while (offset < byteCount) {
      int read = in.read(result, offset, byteCount - offset);
      if (read == -1) {
        throw new EOFException();
      }
      offset += read;
    }
    return new ByteString(result);
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeInt(data.length);
    out.write(data);
  }
}
