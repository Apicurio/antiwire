/*
 * Copyright (C) 2016 Square, Inc.
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
package com.squareup.wire.schema.internal;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.OptionReader;
import com.squareup.wire.schema.internal.parser.SyntaxReader;
import java.util.ArrayList;
import java.util.List;

/** Parses {@code .wire} files. */
public final class ProfileParser {
  private final Location location;
  private final SyntaxReader reader;
  private final List<String> imports = new ArrayList<>();
  private final List<TypeConfigElement> typeConfigs = new ArrayList<>();

  /** Output package name, or null if none yet encountered. */
  private String packageName;

  public ProfileParser(Location location, String data) {
    this.location = location;
    this.reader = new SyntaxReader(data.toCharArray(), location);
  }

  public ProfileFileElement read() {
    String label = reader.readWord();
    reader.expect(label.equals("syntax"), "expected 'syntax'");
    reader.require('=');
    String syntaxString = reader.readQuotedString();
    reader.expect(syntaxString.equals("wire2"), "expected 'wire2'");
    reader.require(';');

    while (true) {
      String documentation = reader.readDocumentation();
      if (reader.exhausted()) {
        return new ProfileFileElement(location, packageName, imports, typeConfigs);
      }

      readDeclaration(documentation);
    }
  }

  private void readDeclaration(String documentation) {
    Location location = reader.location();
    String label = reader.readWord();

    if (label.equals("package")) {
      reader.expect(packageName == null, location, "too many package names");
      packageName = reader.readName(true, false, true);
      reader.require(';');
    } else if (label.equals("import")) {
      String importString = reader.readString();
      imports.add(importString);
      reader.require(';');
    } else if (label.equals("type")) {
      typeConfigs.add(readTypeConfig(location, documentation));
    } else {
      throw reader.unexpected("unexpected label: " + label, location);
    }
  }

  /** Reads a type config and returns it. */
  private TypeConfigElement readTypeConfig(Location location, String documentation) {
    String name = reader.readDataType();
    List<OptionElement> withOptions = new ArrayList<>();
    String target = null;
    String adapter = null;

    reader.require('{');
    while (!reader.peekChar('}')) {
      Location wordLocation = reader.location();
      String word = reader.readWord();
      if (word.equals("target")) {
        reader.expect(target == null, wordLocation, "too many targets");
        target = reader.readWord();
        reader.expect(reader.readWord().equals("using"), "expected 'using'");
        String adapterType = reader.readWord();
        reader.require('#');
        String adapterConstant = reader.readWord();
        reader.require(';');
        adapter = adapterType + "#" + adapterConstant;
      } else if (word.equals("with")) {
        withOptions.add(new OptionReader(reader).readOption('='));
        reader.require(';');
      } else {
        throw reader.unexpected("unexpected label: " + word, wordLocation);
      }
    }

    return new TypeConfigElement(location, name, documentation, withOptions, target, adapter);
  }
}
