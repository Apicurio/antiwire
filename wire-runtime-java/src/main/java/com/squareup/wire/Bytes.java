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
package com.squareup.wire;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import okio.ByteString;

/**
 * An immutable sequence of bytes: this port's wire-owned bytes value for the JDK-typed
 * consumer API (docs/api-surface.md). The engine keeps {@link okio.ByteString} internally;
 * {@code Bytes} is the boundary type that keeps okio out of non-deprecated consumer
 * signatures while generated and fixture code continues to compile against the okio forms.
 *
 * <p>The payload is defensively copied on construction and never exposed: {@link #toByteArray}
 * returns a copy. Package-private bridges hand the internal array to same-package engine
 * code without copying; they are not part of the public surface.
 */
public final class Bytes {
  private static final char[] HEX_DIGITS =
      { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

  /** An empty {@code Bytes}. */
  public static final Bytes EMPTY = new Bytes(new byte[0]);

  private final byte[] data;
  private int hashCode; // Lazily computed; 0 if unknown.

  private Bytes(byte[] data) {
    this.data = data; // Trusted internal constructor doesn't clone data.
  }

  /** Returns a new {@code Bytes} containing a copy of {@code data}. */
  public static Bytes of(byte[] data) {
    if (data == null) throw new IllegalArgumentException("data == null");
    return new Bytes(data.clone());
  }

  /** Returns a new {@code Bytes} containing a copy of {@code byteCount} bytes of {@code data}
   * starting at {@code offset}. */
  public static Bytes of(byte[] data, int offset, int byteCount) {
    if (data == null) throw new IllegalArgumentException("data == null");
    if ((offset | byteCount) < 0 || offset > data.length || data.length - offset < byteCount) {
      throw new ArrayIndexOutOfBoundsException(
          String.format("size=%s offset=%s byteCount=%s", data.length, offset, byteCount));
    }
    byte[] copy = new byte[byteCount];
    System.arraycopy(data, offset, copy, 0, byteCount);
    return new Bytes(copy);
  }

  /** Returns a new {@code Bytes} containing the UTF-8 bytes of {@code s}. */
  public static Bytes encodeUtf8(String s) {
    if (s == null) throw new IllegalArgumentException("s == null");
    return new Bytes(s.getBytes(StandardCharsets.UTF_8));
  }

  /** Decodes the hex-encoded bytes and returns their value as a {@code Bytes}. */
  public static Bytes decodeHex(String hex) {
    // Delegates to the vendored okio decoder; the one-copy conversion is the engine bridge.
    return fromByteString(ByteString.decodeHex(hex));
  }

  /**
   * Decodes the Base64-encoded bytes and returns their value as a {@code Bytes}. Returns null
   * if {@code base64} is not a Base64-encoded sequence of bytes.
   */
  public static Bytes decodeBase64(String base64) {
    ByteString decoded = ByteString.decodeBase64(base64);
    return decoded != null ? fromByteString(decoded) : null;
  }

  /** Returns the number of bytes in this {@code Bytes}. */
  public int size() {
    return data.length;
  }

  /** Returns a byte array containing a copy of the bytes in this {@code Bytes}. */
  public byte[] toByteArray() {
    return data.clone();
  }

  /** Returns a {@code String} decoded from the UTF-8 bytes of this {@code Bytes}. */
  public String utf8() {
    return new String(data, StandardCharsets.UTF_8);
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    return other instanceof Bytes && Arrays.equals(data, ((Bytes) other).data);
  }

  @Override public int hashCode() {
    int result = hashCode;
    // Lazily computed; 0 if unknown. We don't care if we double-allocate in racy code.
    return result != 0 ? result : (hashCode = Arrays.hashCode(data));
  }

  /**
   * Returns a human-readable form: {@code Bytes[size=0]} when empty, otherwise up to 64 bytes
   * as lowercase hexadecimal with the total size, in the spirit of okio's {@code toString}.
   */
  @Override public String toString() {
    if (data.length == 0) return "Bytes[size=0]";
    int hexByteCount = Math.min(data.length, 64);
    StringBuilder result = new StringBuilder("Bytes[");
    if (data.length > 64) result.append("size=").append(data.length).append(' ');
    result.append("hex=");
    for (int i = 0; i < hexByteCount; i++) {
      result.append(HEX_DIGITS[(data[i] >> 4) & 0xf]);
      result.append(HEX_DIGITS[data[i] & 0xf]);
    }
    if (data.length > 64) result.append('…');
    return result.append(']').toString();
  }

  /** Engine bridge: takes ownership of {@code data} without copying; callers hand over the array. */
  static Bytes takeOwnership(byte[] data) {
    return new Bytes(data);
  }

  /** Engine bridge: the internal array. Callers must not mutate it. */
  byte[] internalBytes() {
    return data;
  }

  /** Engine bridge: converts from the engine's okio form, copying once. */
  static Bytes fromByteString(ByteString bytes) {
    return new Bytes(bytes.toByteArray());
  }

  /** Engine bridge: converts to the engine's okio form, copying once. */
  ByteString toByteString() {
    return ByteString.of(data);
  }
}
