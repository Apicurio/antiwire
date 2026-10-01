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

import com.squareup.wire.schema.internal.parser.OptionElement.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OptionReader {
  final SyntaxReader reader;

  public OptionReader(SyntaxReader reader) {
    this.reader = reader;
  }

  /**
   * Reads options enclosed in '[' and ']' if they are present and returns them. Returns an empty
   * list if no options are present.
   */
  public List<OptionElement> readOptions() {
    if (!reader.peekChar('[')) return new ArrayList<>();

    List<OptionElement> result = new ArrayList<>();
    while (true) {
      result.add(readOption('='));

      // Check for closing ']'.
      if (reader.peekChar(']')) break;

      // Discard optional ','.
      reader.expect(reader.peekChar(','), "Expected ',' or ']'");
    }
    return result;
  }

  /** Reads an option containing a name, an '=' or ':', and a value. */
  public OptionElement readOption(char keyValueSeparator) {
    boolean isExtension = reader.peekChar() == '[';
    boolean isParenthesized = reader.peekChar() == '(';
    String name = reader.readName(true, false, true); // Option name.
    if (isExtension) name = "[" + name + "]";

    List<String> subNames = new ArrayList<>();
    char c;
    while (true) {
      c = reader.readChar();
      if (c != '.') {
        break;
      }
      // Read nested field name. For example "baz" in "(foo.bar).baz = 12".
      subNames.add(reader.readName(true, true, false));
    }
    if (keyValueSeparator == ':' && c == '{') {
      // In text format, values which are maps can omit a separator. Backtrack so it can be
      // re-read.
      reader.pushBack('{');
    } else {
      reader.expect(c == keyValueSeparator, "expected '" + keyValueSeparator + "' in option");
    }
    KindAndValue kindAndValue = readKindAndValue();
    Kind kind = kindAndValue.kind;
    Object value = kindAndValue.value;
    for (int i = subNames.size() - 1; i >= 0; i--) {
      boolean parenthesized = false;
      String subName = subNames.get(i);
      if (subName.startsWith("(")) {
        subName = subName.substring(1, subName.length() - 1);
        parenthesized = true;
      }
      value = OptionElement.create(subName, kind, value, parenthesized);
      kind = Kind.OPTION;
    }
    return OptionElement.create(name, kind, value, isParenthesized);
  }

  /** Reads a value that can be a map, list, string, number, boolean or enum. */
  private KindAndValue readKindAndValue() {
    char peeked = reader.peekChar();
    switch (peeked) {
      case '{':
        return new KindAndValue(Kind.MAP, readMap('{', '}', ':'));
      case '[':
        return new KindAndValue(Kind.LIST, readList());
      case '"':
      case '\'':
        return new KindAndValue(Kind.STRING, reader.readString());
      default:
        if (Character.isDigit(peeked) || peeked == '-') {
          return new KindAndValue(Kind.NUMBER, reader.readWord());
        }
        String word = reader.readWord();
        if (word.equals("true")) return new KindAndValue(Kind.BOOLEAN, "true");
        if (word.equals("false")) return new KindAndValue(Kind.BOOLEAN, "false");
        return new KindAndValue(Kind.ENUM, word);
    }
  }

  /**
   * Returns a map of string keys and values. This is similar to a JSON object, with '{' and '}'
   * surrounding the map, ':' separating keys from values, and ',' or ';' separating entries.
   */
  private Map<String, Object> readMap(char openBrace, char closeBrace, char keyValueSeparator) {
    if (reader.readChar() != openBrace) throw new AssertionError();
    Map<String, Object> result = new LinkedHashMap<>();
    while (true) {
      if (reader.peekChar(closeBrace)) {
        // If we see the close brace, finish immediately. This handles {}/[] and ,}/,] cases.
        return result;
      }

      OptionElement option = readOption(keyValueSeparator);
      String name = option.name;
      Object value = option.kind == Kind.BOOLEAN || option.kind == Kind.ENUM
          || option.kind == Kind.NUMBER
          ? new OptionElement.OptionPrimitive(option.kind, option.value)
          : option.value;

      if (value instanceof OptionElement) {
        OptionElement element = (OptionElement) value;
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) result.get(name);
        if (nested == null) {
          nested = new LinkedHashMap<>();
          result.put(name, nested);
        }
        nested.put(element.name, element.value);
      } else {
        // Add the value(s) to any previous values with the same key.
        Object previous = result.get(name);
        if (previous == null) {
          result.put(name, value);
        } else if (previous instanceof List) {
          addToList((List<Object>) previous, value);
        } else {
          List<Object> newList = new ArrayList<>();
          newList.add(previous);
          addToList(newList, value);
          result.put(name, newList);
        }
      }

      // Discard optional separator.
      reader.peekChar(',');
      reader.peekChar(';');
    }
  }

  /** Adds an object or objects to a list. */
  @SuppressWarnings("unchecked")
  private static void addToList(List<Object> list, Object value) {
    if (value instanceof List) {
      list.addAll((List<Object>) value);
    } else {
      list.add(value);
    }
  }

  /**
   * Returns a list of values. This is similar to JSON with '[' and ']' surrounding the list and
   * ',' separating values.
   */
  @SuppressWarnings("unchecked")
  private List<Object> readList() {
    reader.require('[');
    List<Object> result = new ArrayList<>();
    while (true) {
      // If we see the close brace, finish immediately. This handles [] and ,] cases.
      if (reader.peekChar(']')) return result;
      KindAndValue option = readKindAndValue();
      Object value = option.kind == Kind.BOOLEAN || option.kind == Kind.ENUM
          || option.kind == Kind.NUMBER
          ? new OptionElement.OptionPrimitive(option.kind, option.value)
          : option.value;
      result.add(value);

      if (reader.peekChar(',')) continue;
      reader.expect(reader.peekChar() == ']', "expected ',' or ']'");
    }
  }

  static final class KindAndValue {
    final Kind kind;
    final Object value;

    KindAndValue(Kind kind, Object value) {
      this.kind = kind;
      this.value = value;
    }
  }
}
