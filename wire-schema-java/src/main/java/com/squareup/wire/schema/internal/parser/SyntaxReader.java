/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.wire.schema.internal.parser;

import com.squareup.wire.schema.Location;

/** A general purpose reader for formats like `.proto`. */
public final class SyntaxReader {
  private final char[] data;
  private final Location location;

  /** Our cursor within the document. data[pos] is the next character to be read. */
  private int pos;

  /** The number of newline characters encountered thus far. */
  private int line;

  /** The index of the most recent newline character. */
  private int lineStart;

  public SyntaxReader(char[] data, Location location) {
    this.data = data;
    this.location = location;
  }

  public boolean exhausted() {
    return pos == data.length;
  }

  /** Reads a non-whitespace character and returns it. */
  public char readChar() {
    char result = peekChar();
    pos++;
    return result;
  }

  /** Reads a non-whitespace character c, or throws an exception. */
  public void require(char c) {
    char readChar = readChar();
    expect(readChar == c, "expected '" + c + "' but was '" + readChar + "'");
  }

  /**
   * Peeks a non-whitespace character and returns it. The only difference between this and
   * readChar() is that this doesn't consume the char.
   */
  public char peekChar() {
    skipWhitespace(true);
    expect(pos < data.length, "unexpected end of file");
    return data[pos];
  }

  /**
   * Note that although the name suggests otherwise, this does consume the char if it finds it.
   */
  public boolean peekChar(char c) {
    if (peekChar() == c) {
      pos++;
      return true;
    }
    return false;
  }

  /** Push back the most recently read character. */
  public void pushBack(char c) {
    if (data[pos - 1] != c) {
      throw new IllegalArgumentException("Failed requirement.");
    }
    pos--;
  }

  /** Reads a quoted or unquoted string and returns it. */
  public String readString() {
    skipWhitespace(true);
    char peeked = peekChar();
    if (peeked == '"' || peeked == '\'') {
      return readQuotedString();
    }
    return readWord(true, true);
  }

  public String readQuotedString() {
    char startQuote = readChar();
    expect(startQuote == '"' || startQuote == '\'', "expected quoted string");
    StringBuilder result = new StringBuilder();
    while (pos < data.length) {
      char c = data[pos++];
      if (c == startQuote) {
        // Adjacent strings are concatenated. Consume new quote and continue reading.
        if (peekChar() == '"' || peekChar() == '\'') {
          startQuote = readChar();
          continue;
        }
        return result.toString();
      }
      if (c == '\\') {
        expect(pos < data.length, "unexpected end of file");
        c = data[pos++];
        switch (c) {
          case 'a': c = '\u0007'; break; // Alert.
          case 'b': c = '\b'; break; // Backspace.
          case 'f': c = '\f'; break; // Form feed.
          case 'n': c = '\n'; break; // Newline.
          case 'r': c = '\r'; break; // Carriage return.
          case 't': c = '\t'; break; // Horizontal tab.
          case 'v': c = '\u000b'; break; // Vertical tab.
          case 'x': case 'X':
            c = readNumericEscape(16, 2);
            break;
          case '0': case '1': case '2': case '3':
          case '4': case '5': case '6': case '7':
            pos--;
            c = readNumericEscape(8, 3);
            break;
          default:
            break;
        }
      }
      result.append(c);
      if (c == '\n') newline();
    }
    throw unexpected("unterminated string");
  }

  private char readNumericEscape(int radix, int len) {
    int value = -1;
    int endPos = Math.min(pos + len, data.length);
    while (pos < endPos) {
      int digit = hexDigit(data[pos]);
      if (digit == -1 || digit >= radix) break;
      value = value < 0 ? digit : value * radix + digit;
      pos++;
    }
    expect(value >= 0, "expected a digit after \\x or \\X");
    return (char) value;
  }

  private static int hexDigit(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
  }

  /**
   * Reads a (paren-wrapped), [square-wrapped] or naked symbol name. If retainWrap is true and the
   * symbol was wrapped in parens or square brackets, the returned string retains the wrapping
   * punctuation. Otherwise, just the symbol is returned.
   */
  public String readName(boolean allowLeadingDigit, boolean retainWrap, boolean allowDots) {
    switch (peekChar()) {
      case '(':
        pos++;
        String parenWord = readWord(allowLeadingDigit);
        expect(readChar() == ')', "expected ')'");
        return retainWrap ? "(" + parenWord + ")" : parenWord;

      case '[':
        pos++;
        String bracketWord = readWord(allowLeadingDigit);
        expect(readChar() == ']', "expected ']'");
        return retainWrap ? "[" + bracketWord + "]" : bracketWord;

      default:
        return readWord(allowLeadingDigit, allowDots);
    }
  }

  /** Reads a non-empty word allowing dots (the Kotlin default signature). */
  public String readWord() {
    return readWord(true, true);
  }

  /** Reads a scalar, map, or type name. */
  public String readDataType() {
    String name = readWord();
    return readDataType(name);
  }

  /** Reads a scalar, map, or type name with name as a prefix word. */
  public String readDataType(String name) {
    if (name.equals("map")) {
      expect(readChar() == '<', "expected '<'");
      String keyType = readDataType();
      expect(readChar() == ',', "expected ','");
      String valueType = readDataType();
      expect(readChar() == '>', "expected '>'");
      return "map<" + keyType + ", " + valueType + ">";
    }
    StringBuilder result = new StringBuilder();
    result.append(name);
    while (peekChar() == '.') {
      pos++; // We skip the dot so we can read the next word.
      result.append('.').append(readWord());
    }
    return result.toString();
  }

  /**
   * Reads a non-empty word and returns it.
   */
  public String readWord(boolean allowLeadingDigit, boolean allowDots) {
    skipWhitespace(true);
    int start = pos;
    while (pos < data.length) {
      char c = data[pos];
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
          || c == '_' || c == '-') {
        pos++;
      } else if (c == '.') {
        if (allowDots) {
          pos++;
        } else {
          break;
        }
      } else {
        break;
      }
    }
    expect(start < pos, "expected a word");
    if (!allowLeadingDigit && Character.isDigit(data[start])) {
      // Upstream keeps the column without the +1 offset in this specific check.
      throw unexpectedAt(location.at(line + 1, start - lineStart),
          "field and constant names cannot start with a digit");
    }
    return new String(data, start, pos - start);
  }

  public String readWord(boolean allowLeadingDigit) {
    return readWord(allowLeadingDigit, true);
  }

  /** Reads an integer and returns it. */
  public int readInt() {
    String tag = readWord();
    try {
      int radix = 10;
      if (tag.startsWith("0x") || tag.startsWith("0X")) {
        tag = tag.substring(2);
        radix = 16;
      }
      return Integer.parseInt(tag, radix);
    } catch (Exception e) {
      throw unexpected("expected an integer but was " + tag);
    }
  }

  /**
   * Like skipWhitespace(boolean), but this returns a string containing all comment text. By
   * convention, comments before a declaration document that declaration.
   */
  public String readDocumentation() {
    String result = null;
    while (true) {
      skipWhitespace(false);
      if (pos == data.length || data[pos] != '/') return result != null ? result : "";
      String comment = readComment();
      result = result == null ? comment : result + "\n" + comment;
    }
  }

  /** Reads a comment and returns its body. */
  private String readComment() {
    expect(pos < data.length && data[pos] == '/', "expected a comment");
    pos++;
    int next = pos < data.length ? data[pos++] : -1;
    switch (next) {
      case '*': {
        StringBuilder result = new StringBuilder();
        boolean startOfLine = true;
        while (pos + 1 < data.length) {
          char c = data[pos];
          if (c == '*' && data[pos + 1] == '/') {
            pos += 2;
            return result.toString().trim();
          }
          if (c == '\n') {
            result.append('\n');
            newline();
            startOfLine = true;
          } else if (!startOfLine) {
            result.append(c);
          } else if (c == '*') {
            if (data[pos + 1] == ' ') {
              pos += 1; // Skip a single leading space, if present.
            }
            startOfLine = false;
          } else if (!Character.isWhitespace(c)) {
            result.append(c);
            startOfLine = false;
          }
          pos++;
        }
        throw unexpected("unterminated comment");
      }
      case '/': {
        if (pos < data.length && data[pos] == ' ') {
          pos++; // Skip a single leading space, if present.
        }
        int start = pos;
        while (pos < data.length) {
          char c = data[pos++];
          if (c == '\n') {
            newline();
            break;
          }
        }
        return new String(data, start, pos - 1 - start);
      }
      default:
        throw unexpected("unexpected '/'");
    }
  }

  public String tryAppendTrailingDocumentation(String documentation) {
    // Search for a '/' character ignoring spaces and tabs.
    while (pos < data.length) {
      char c = data[pos];
      if (c == ' ' || c == '\t') {
        pos++;
      } else if (c == '/') {
        pos++;
        break;
      } else {
        // Not a whitespace or comment-starting character. Return original documentation.
        return documentation;
      }
    }
    if (!(pos < data.length && (data[pos] == '/' || data[pos] == '*'))) {
      pos--; // Backtrack to start of comment.
      expect(false, "expected '//' or '/*'");
    }
    boolean isStar = data[pos] == '*';
    pos++;
    // Skip a single leading space, if present.
    if (pos < data.length && data[pos] == ' ') pos++;
    int start = pos;
    int end;
    if (isStar) {
      // Consume star comment until it closes on the same line.
      while (true) {
        expect(pos < data.length, "trailing comment must be closed");
        if (data[pos] == '*' && pos + 1 < data.length && data[pos + 1] == '/') {
          end = pos - 1; // The character before '*'.
          pos += 2; // Skip to the character after '/'.
          break;
        }
        pos++;
      }
      // Ensure nothing follows a trailing star comment.
      while (pos < data.length) {
        char c = data[pos++];
        if (c == '\n') {
          newline();
          break;
        }
        expect(c == ' ' || c == '\t' || c == '\r', "no syntax may follow trailing comment");
      }
    } else {
      // Consume slash comment until it ends.
      while (pos < data.length && data[pos] != '\n') {
        pos++;
      }
      end = pos - 1; // The character before '\n'.
    }
    if (end == start) return documentation; // Empty comment: keep the original.
    String trailing = new String(data, start, end - start);
    while (!trailing.isEmpty()
        && (trailing.endsWith(" ") || trailing.endsWith("\t") || trailing.endsWith("\r"))) {
      trailing = trailing.substring(0, trailing.length() - 1);
    }
    return documentation.isEmpty() ? trailing : documentation + "\n" + trailing;
  }

  public Location location() {
    return location.at(line + 1, pos - lineStart + 1);
  }

  /** Call this every time a newline is encountered. */
  private void newline() {
    line++;
    lineStart = pos;
  }

  private void skipWhitespace(boolean skipComments) {
    while (pos < data.length) {
      char c = data[pos];
      if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
        pos++;
        if (c == '\n') newline();
      } else if (skipComments && c == '/') {
        readComment();
      } else {
        return;
      }
    }
  }

  public void expect(boolean condition, String message) {
    if (!condition) throw unexpected(message);
  }

  public void expect(boolean condition, Location at, String message) {
    if (!condition) throw unexpected(message, at);
  }

  public RuntimeException unexpected(String message) {
    return unexpected(message, location());
  }

  public RuntimeException unexpected(String message, Location at) {
    return new IllegalStateException("Syntax error in " + at + ": " + message);
  }

  /** Upstream keeps the column without the +1 offset in this specific check. */
  public RuntimeException unexpectedAt(Location at, String message) {
    return unexpected(message, at);
  }
}
