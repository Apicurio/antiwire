/*
 * Copyright (C) 2026 Square, Inc.
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
package com.squareup.wire.schema;

import java.util.regex.Pattern;

/**
 * Validates that a default or option string literal is in range for its declared type. Numeric
 * literals are compared as decimal strings so unsigned 64-bit values above {@link Long#MAX_VALUE}
 * are handled without overflow.
 */
final class LiteralValidation {
  private static final String INT32_MAX = "2147483647";
  private static final String INT32_MIN = "-2147483648";
  private static final String UINT32_MAX = "4294967295";
  private static final String INT64_MAX = "9223372036854775807";
  private static final String INT64_MIN = "-9223372036854775808";
  private static final String UINT64_MAX = "18446744073709551615";

  private static final Pattern DECIMAL_INTEGER = Pattern.compile("-?[0-9]+");
  private static final Pattern FLOATING_POINT =
      Pattern.compile("-?((([0-9]+)(\\.[0-9]*)?)|(\\.[0-9]+))([eE][+-]?[0-9]+)?");
  private static final Pattern HEX_INTEGER = Pattern.compile("-?0[xX][0-9a-fA-F]+");

  static boolean isValidLiteral(Linker linker, ProtoType type, String value) {
    if (type.equals(ProtoType.BOOL)) return value.equals("true") || value.equals("false");
    if (type.equals(ProtoType.BYTES) || type.equals(ProtoType.STRING)) return true;
    if (type.equals(ProtoType.DOUBLE) || type.equals(ProtoType.FLOAT)) {
      return isValidFloatingPointDefault(value);
    }
    if (type.equals(ProtoType.FIXED32) || type.equals(ProtoType.UINT32)) {
      return isValidUnsignedIntegerDefault(value, UINT32_MAX);
    }
    if (type.equals(ProtoType.FIXED64) || type.equals(ProtoType.UINT64)) {
      return isValidUnsignedIntegerDefault(value, UINT64_MAX);
    }
    if (type.equals(ProtoType.INT32) || type.equals(ProtoType.SFIXED32)
        || type.equals(ProtoType.SINT32)) {
      return isValidSignedIntegerDefault(value, INT32_MIN, INT32_MAX);
    }
    if (type.equals(ProtoType.INT64) || type.equals(ProtoType.SFIXED64)
        || type.equals(ProtoType.SINT64)) {
      return isValidSignedIntegerDefault(value, INT64_MIN, INT64_MAX);
    }
    Type valueType = linker.get(type);
    return valueType instanceof EnumType && ((EnumType) valueType).constant(value) != null;
  }

  private static boolean isValidFloatingPointDefault(String value) {
    if (value.equals("inf") || value.equals("-inf") || value.equals("nan")
        || value.equals("-nan")) {
      return true;
    }
    if (!FLOATING_POINT.matcher(value).matches()) return false;
    try {
      Double.parseDouble(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isValidSignedIntegerDefault(String value, String min, String max) {
    if (!isIntegerLiteral(value) || hasOctalPrefix(value)) return false;
    return compareIntegerLiterals(min, value) <= 0 && compareIntegerLiterals(value, max) <= 0;
  }

  private static boolean isValidUnsignedIntegerDefault(String value, String max) {
    if (!isIntegerLiteral(value) || hasOctalPrefix(value) || value.startsWith("-")) return false;
    return compareIntegerLiterals(value, max) <= 0;
  }

  private static boolean isIntegerLiteral(String value) {
    return DECIMAL_INTEGER.matcher(value).matches() || HEX_INTEGER.matcher(value).matches();
  }

  private static boolean hasOctalPrefix(String value) {
    String digits = value.startsWith("-") ? value.substring(1) : value;
    return digits.length() > 1 && digits.charAt(0) == '0' && digits.charAt(1) != 'x'
        && digits.charAt(1) != 'X';
  }

  private static int compareIntegerLiterals(String a, String b) {
    boolean aNegative = a.startsWith("-");
    boolean bNegative = b.startsWith("-");
    if (aNegative != bNegative) return aNegative ? -1 : 1;

    int magnitudeComparison = compareIntegerMagnitudes(
        aNegative ? a.substring(1) : a,
        bNegative ? b.substring(1) : b);
    return aNegative ? -magnitudeComparison : magnitudeComparison;
  }

  private static int compareIntegerMagnitudes(String a, String b) {
    String aDecimal = decimalMagnitude(a);
    String bDecimal = decimalMagnitude(b);
    if (aDecimal.length() != bDecimal.length()) {
      return Integer.compare(aDecimal.length(), bDecimal.length());
    }
    return aDecimal.compareTo(bDecimal);
  }

  private static String decimalMagnitude(String value) {
    String stripped;
    if (value.startsWith("0x") || value.startsWith("0X")) {
      stripped = hexMagnitudeToDecimal(value.substring(2));
    } else {
      stripped = trimLeadingZeros(value);
    }
    return trimLeadingZeros(stripped);
  }

  private static String trimLeadingZeros(String value) {
    int i = 0;
    while (i < value.length() - 1 && value.charAt(i) == '0') {
      i++;
    }
    String trimmed = value.substring(i);
    return trimmed.isEmpty() ? "0" : trimmed;
  }

  private static String hexMagnitudeToDecimal(String hex) {
    String decimal = "0";
    for (int i = 0; i < hex.length(); i++) {
      int digit = Character.digit(hex.charAt(i), 16);
      decimal = multiplyDecimalBy(decimal, 16);
      decimal = addDecimal(decimal, digit);
    }
    return decimal;
  }

  private static String multiplyDecimalBy(String value, int multiplier) {
    int carry = 0;
    StringBuilder result = new StringBuilder(value.length() + 2);
    for (int i = value.length() - 1; i >= 0; i--) {
      int product = (value.charAt(i) - '0') * multiplier + carry;
      result.append((char) ('0' + product % 10));
      carry = product / 10;
    }
    while (carry > 0) {
      result.append((char) ('0' + carry % 10));
      carry /= 10;
    }
    return trimLeadingZeros(result.reverse().toString());
  }

  private static String addDecimal(String value, int addend) {
    int carry = addend;
    StringBuilder result = new StringBuilder(value.length() + 2);
    for (int i = value.length() - 1; i >= 0; i--) {
      int sum = (value.charAt(i) - '0') + carry;
      result.append((char) ('0' + sum % 10));
      carry = sum / 10;
    }
    while (carry > 0) {
      result.append((char) ('0' + carry % 10));
      carry /= 10;
    }
    return trimLeadingZeros(result.reverse().toString());
  }

  private LiteralValidation() {
    throw new AssertionError("no instances");
  }
}
