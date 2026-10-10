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

import com.squareup.wire.Syntax;
import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Basic parser for `.proto` schema declarations. */
public final class ProtoParser {
  private final Location location;
  private final SyntaxReader reader;
  private final List<String> imports = new ArrayList<>();
  private final List<String> publicImports = new ArrayList<>();
  private final List<String> weakImports = new ArrayList<>();
  private final List<TypeElement> nestedTypes = new ArrayList<>();
  private final List<ServiceElement> services = new ArrayList<>();
  private final List<ExtendElement> extendsList = new ArrayList<>();
  private final List<OptionElement> options = new ArrayList<>();

  /** The number of declarations defined in the current file. */
  private int declarationCount;

  /** The syntax of the file, or null if none is defined. */
  private Syntax syntax;

  /** Output package name, or null if none yet encountered. */
  private String packageName;

  /** The current package name + nested type names, separated by dots. */
  private String prefix = "";

  private ProtoParser(Location location, char[] data) {
    this.location = location;
    this.reader = new SyntaxReader(data, location);
  }

  public ProtoFileElement readProtoFile() {
    while (true) {
      String documentation = reader.readDocumentation();
      if (reader.exhausted()) {
        return new ProtoFileElement(location, packageName, syntax, withUnixSlashes(imports),
            withUnixSlashes(publicImports), withUnixSlashes(weakImports), nestedTypes, services,
            extendsList, options);
      }

      Object declaration = readDeclaration(documentation, Context.FILE);
      if (declaration instanceof TypeElement) {
        TypeElement element = (TypeElement) declaration;
        TypeElement duplicate = findNestedType(element.getName());
        if (duplicate != null) {
          throw new IllegalStateException(element.getName() + " (" + element.getLocation() + ") is"
              + " already defined at " + duplicate.getLocation());
        }
        nestedTypes.add(element);
      } else if (declaration instanceof ServiceElement) {
        ServiceElement element = (ServiceElement) declaration;
        ServiceElement duplicate = findService(element.getName());
        if (duplicate != null) {
          throw new IllegalStateException(element.getName() + " (" + element.getLocation() + ") is already"
              + " defined at " + duplicate.getLocation());
        }
        services.add(element);
      } else if (declaration instanceof OptionElement) {
        options.add((OptionElement) declaration);
      } else if (declaration instanceof ExtendElement) {
        extendsList.add((ExtendElement) declaration);
      }
    }
  }

  private TypeElement findNestedType(String name) {
    for (TypeElement type : nestedTypes) {
      if (type.getName().equals(name)) return type;
    }
    return null;
  }

  private ServiceElement findService(String name) {
    for (ServiceElement service : services) {
      if (service.getName().equals(name)) return service;
    }
    return null;
  }

  /**
   * Upstream normalizes import paths through okio's Path.withUnixSlashes: backslashes to
   * forward slashes, duplicate slashes collapsed, and leading "./" segments resolved.
   */
  private static List<String> withUnixSlashes(List<String> paths) {
    List<String> result = new ArrayList<>(paths.size());
    for (String path : paths) {
      String normalized = path.replace('\\', '/').replaceAll("/{2,}", "/");
      while (normalized.startsWith("./")) {
        normalized = normalized.substring(2);
      }
      result.add(normalized);
    }
    return result;
  }

  private Object readDeclaration(String documentation, Context context) {
    int index = declarationCount++;

    // Skip unnecessary semicolons, occasionally used after a nested message declaration.
    if (reader.peekChar(';')) return null;

    Location location = reader.location();
    String label = reader.readWord(context != Context.ENUM);

    if ("package".equals(label) && context.permitsPackage()) {
      packageName = reader.readName(true, false, true);
      prefix = packageName + ".";
      reader.require(';');
      return null;
    }

    if ("import".equals(label) && context.permitsImport()) {
      char peeked = reader.peekChar();
      if (peeked == '"' || peeked == '\'') {
        imports.add(reader.readQuotedString());
      } else {
        String kind = reader.readString();
        switch (kind) {
          case "weak":
            weakImports.add(reader.readQuotedString());
            break;
          case "public":
            publicImports.add(reader.readQuotedString());
            break;
          default:
            throw reader.unexpected("expected quoted string", location);
        }
      }
      reader.require(';');
      return null;
    }

    if ("syntax".equals(label) && context.permitsSyntax()) {
      reader.expect(syntax == null, location, "too many syntax definitions");
      reader.require('=');
      reader.expect(index == 0, location,
          "'syntax' element must be the first declaration in a file");
      String syntaxString = reader.readQuotedString();
      try {
        syntax = Syntax.get(syntaxString);
      } catch (IllegalArgumentException e) {
        throw reader.unexpected(e.getMessage(), location);
      }
      reader.require(';');
      return null;
    }

    if ("option".equals(label)) {
      OptionElement option = new OptionReader(reader).readOption('=');
      reader.require(';');
      return option;
    }

    if ("reserved".equals(label)) return readReserved(location, documentation);
    if ("message".equals(label) && context.permitsMessage()) {
      return readMessage(location, documentation);
    }
    if ("enum".equals(label) && context.permitsEnum()) {
      return readEnumElement(location, documentation);
    }
    if ("service".equals(label) && context.permitsService()) {
      return readService(location, documentation);
    }
    if ("extend".equals(label) && context.permitsExtend()) {
      return readExtend(location, documentation);
    }
    if ("rpc".equals(label) && context.permitsRpc()) {
      return readRpc(location, documentation);
    }
    if ("oneof".equals(label) && context.permitsOneOf()) {
      return readOneOf(location, documentation);
    }
    if ("extensions".equals(label) && context.permitsExtensions()) {
      return readExtensions(location, documentation);
    }

    if (context == Context.MESSAGE || context == Context.EXTEND) {
      return readField(documentation, location, label);
    }

    if (context == Context.ENUM) {
      return readEnumConstant(documentation, location, label);
    }

    if ("edition".equals(label) && context.permitsEdition()) {
      throw reader.unexpected("edition is not currently supported");
    }

    throw reader.unexpected("unexpected label: " + label, location);
  }

  /** Reads a message declaration. */
  private MessageElement readMessage(Location location, String documentation) {
    String name = reader.readName(true, false, true);
    List<FieldElement> fields = new ArrayList<>();
    List<OneOfElement> oneOfs = new ArrayList<>();
    List<TypeElement> nestedTypes = new ArrayList<>();
    List<ExtensionsElement> extensions = new ArrayList<>();
    List<OptionElement> options = new ArrayList<>();
    List<ReservedElement> reserveds = new ArrayList<>();
    List<GroupElement> groups = new ArrayList<>();
    List<ExtendElement> extendDeclarations = new ArrayList<>();

    String previousPrefix = prefix;
    prefix = prefix + name + ".";

    reader.require('{');
    while (true) {
      String nestedDocumentation = reader.readDocumentation();
      if (reader.peekChar('}')) break;

      Object declared = readDeclaration(nestedDocumentation, Context.MESSAGE);
      if (declared instanceof FieldElement) fields.add((FieldElement) declared);
      else if (declared instanceof OneOfElement) oneOfs.add((OneOfElement) declared);
      else if (declared instanceof GroupElement) groups.add((GroupElement) declared);
      else if (declared instanceof TypeElement) nestedTypes.add((TypeElement) declared);
      else if (declared instanceof ExtensionsElement) extensions.add((ExtensionsElement) declared);
      else if (declared instanceof OptionElement) options.add((OptionElement) declared);
      else if (declared instanceof ExtendElement) extendDeclarations.add((ExtendElement) declared);
      else if (declared instanceof ReservedElement) reserveds.add((ReservedElement) declared);
    }

    prefix = previousPrefix;

    return new MessageElement(location, name, documentation, nestedTypes, options, reserveds,
        fields, oneOfs, extensions, groups, extendDeclarations);
  }

  /** Reads an extend declaration. */
  private ExtendElement readExtend(Location location, String documentation) {
    String name = reader.readName(true, false, true);
    List<FieldElement> fields = new ArrayList<>();

    reader.require('{');
    while (true) {
      String nestedDocumentation = reader.readDocumentation();
      if (reader.peekChar('}')) break;

      Object declared = readDeclaration(nestedDocumentation, Context.EXTEND);
      if (declared instanceof FieldElement) fields.add((FieldElement) declared);
    }

    return new ExtendElement(location, name, documentation, fields);
  }

  /** Reads a service declaration and returns it. */
  private ServiceElement readService(Location location, String documentation) {
    String name = reader.readName(true, false, true);
    List<RpcElement> rpcs = new ArrayList<>();
    List<OptionElement> options = new ArrayList<>();

    reader.require('{');
    while (true) {
      String rpcDocumentation = reader.readDocumentation();
      if (reader.peekChar('}')) break;

      Object declared = readDeclaration(rpcDocumentation, Context.SERVICE);
      if (declared instanceof RpcElement) rpcs.add((RpcElement) declared);
      else if (declared instanceof OptionElement) options.add((OptionElement) declared);
    }

    return new ServiceElement(location, name, documentation, rpcs, options);
  }

  /** Reads an enumerated type declaration and returns it. */
  private EnumElement readEnumElement(Location location, String documentation) {
    String name = reader.readName(true, false, true);
    List<EnumConstantElement> constants = new ArrayList<>();
    List<OptionElement> options = new ArrayList<>();
    List<ReservedElement> reserveds = new ArrayList<>();

    reader.require('{');
    while (true) {
      String valueDocumentation = reader.readDocumentation();
      if (reader.peekChar('}')) break;

      Object declared = readDeclaration(valueDocumentation, Context.ENUM);
      if (declared instanceof EnumConstantElement) constants.add((EnumConstantElement) declared);
      else if (declared instanceof OptionElement) options.add((OptionElement) declared);
      else if (declared instanceof ReservedElement) reserveds.add((ReservedElement) declared);
    }

    return new EnumElement(location, name, documentation, options, constants, reserveds);
  }

  private Object readField(String documentation, Location location, String word) {
    Label label;
    String type;
    switch (word) {
      case "required":
        reader.expect(syntax != Syntax.PROTO_3, location,
            "'required' label forbidden in proto3 field declarations");
        label = Label.REQUIRED;
        type = reader.readDataType();
        break;

      case "optional":
        label = Label.OPTIONAL;
        type = reader.readDataType();
        break;

      case "repeated":
        label = Label.REPEATED;
        type = reader.readDataType();
        break;

      default:
        reader.expect(syntax == Syntax.PROTO_3 || ("map".equals(word) && reader.peekChar() == '<'),
            location, "unexpected label: " + word);
        label = null;
        type = reader.readDataType(word);
        break;
    }

    reader.expect(!type.startsWith("map<") || label == null, location,
        "'map' type cannot have label");

    if ("group".equals(type)) {
      return readGroup(location, documentation, label);
    }
    return readField(location, documentation, label, type);
  }

  /** Reads a field declaration and returns it. */
  private FieldElement readField(Location location, String documentation, Label label,
      String type) {
    String name = reader.readName(false, false, true);
    reader.require('=');
    int tag = reader.readInt();

    // Mutable copy to extract the default value, and add packed if necessary.
    List<OptionElement> options = new OptionReader(reader).readOptions();

    String defaultValue = stripValue("default", options);
    String jsonName = stripValue("json_name", options);
    reader.require(';');

    documentation = reader.tryAppendTrailingDocumentation(documentation);

    return new FieldElement(location, label, type, name, defaultValue, jsonName, tag,
        documentation, options);
  }

  /**
   * This finds an option named name, removes, and returns it. Returns null if no name option is
   * present.
   */
  private static String stripValue(String name, List<OptionElement> options) {
    String result = null;
    Iterator<OptionElement> iterator = options.iterator();
    while (iterator.hasNext()) {
      OptionElement element = iterator.next();
      if (element.getName().equals(name)) {
        iterator.remove();
        result = element.getValue().toString();
      }
    }
    return result;
  }

  private OneOfElement readOneOf(Location location, String documentation) {
    String name = reader.readName(true, false, true);
    List<FieldElement> fields = new ArrayList<>();
    List<GroupElement> groups = new ArrayList<>();
    List<OptionElement> options = new ArrayList<>();

    reader.require('{');
    while (true) {
      String nestedDocumentation = reader.readDocumentation();
      if (reader.peekChar('}')) break;

      Location fieldLocation = reader.location();
      String type = reader.readDataType();
      if ("group".equals(type)) {
        groups.add(readGroup(fieldLocation, nestedDocumentation, null));
      } else if ("option".equals(type)) {
        options.add(new OptionReader(reader).readOption('='));
        reader.require(';');
      } else {
        fields.add(readField(fieldLocation, nestedDocumentation, null, type));
      }
    }

    return new OneOfElement(name, documentation, fields, groups, options, location);
  }

  private GroupElement readGroup(Location location, String documentation, Label label) {
    String name = reader.readWord();
    reader.require('=');
    int tag = reader.readInt();
    List<FieldElement> fields = new ArrayList<>();

    reader.require('{');
    while (true) {
      String nestedDocumentation = reader.readDocumentation();
      if (reader.peekChar('}')) break;

      Location fieldLocation = reader.location();
      String fieldLabel = reader.readWord();
      Object field = readField(nestedDocumentation, fieldLocation, fieldLabel);
      if (field instanceof FieldElement) {
        fields.add((FieldElement) field);
      } else {
        throw reader.unexpected("expected field declaration, was " + field);
      }
    }

    return new GroupElement(label, location, name, tag, documentation, fields);
  }

  /** Reads a reserved tags and names list like "reserved 10, 12 to 14, 'foo';". */
  private ReservedElement readReserved(Location location, String documentation) {
    List<Object> values = new ArrayList<>();

    loop:
    while (true) {
      char peeked = reader.peekChar();
      if (peeked == '"' || peeked == '\'') {
        values.add(reader.readQuotedString());
      } else {
        int tagStart = reader.readInt();
        char after = reader.peekChar();
        if (after == ',' || after == ';') {
          values.add(tagStart);
        } else {
          reader.expect(reader.readWord().equals("to"), location,
              "expected ',', ';', or 'to'");
          String s = reader.readWord();
          int tagEnd = "max".equals(s) ? SchemaUtil.MAX_TAG_VALUE : Integer.parseInt(s);
          values.add(new int[] {tagStart, tagEnd});
        }
      }

      char separator = reader.readChar();
      switch (separator) {
        case ';':
          break loop;
        case ',':
          continue;
        default:
          throw reader.unexpected("expected ',' or ';'");
      }
    }

    reader.expect(!values.isEmpty(), location,
        "'reserved' must have at least one field name or tag");

    documentation = reader.tryAppendTrailingDocumentation(documentation);

    return new ReservedElement(location, documentation, values);
  }

  /** Reads extensions like "extensions 101;" or "extensions 101 to max;". */
  private ExtensionsElement readExtensions(Location location, String documentation) {
    List<Object> values = new ArrayList<>();
    List<OptionElement> options = new ArrayList<>();
    loop:
    while (true) {
      int start = reader.readInt();

      char peeked = reader.peekChar();
      if (peeked == ',' || peeked == ';' || peeked == '[') {
        values.add(start);
      } else {
        reader.expect(reader.readWord().equals("to"), location,
            "expected ',', ';', '[' or 'to'");
        String s = reader.readWord();
        int end = "max".equals(s) ? SchemaUtil.MAX_TAG_VALUE : Integer.parseInt(s);
        values.add(new int[] {start, end});
      }
      if (reader.peekChar() == '[') {
        options.addAll(new OptionReader(reader).readOptions());
        reader.require(';');
        break loop;
      }
      char separator = reader.readChar();
      switch (separator) {
        case ';':
          break loop;
        case ',':
          continue;
        default:
          throw reader.unexpected("expected ',' or ';'");
      }
    }

    return new ExtensionsElement(location, documentation, values, options);
  }

  /** Reads an enum constant like "ROCK = 0;". The label is the constant name. */
  private EnumConstantElement readEnumConstant(String documentation, Location location,
      String label) {
    reader.require('=');
    int tag = reader.readInt();

    List<OptionElement> options = new OptionReader(reader).readOptions();
    reader.require(';');

    documentation = reader.tryAppendTrailingDocumentation(documentation);

    return new EnumConstantElement(location, label, tag, documentation, options);
  }

  /** Reads an rpc and returns it. */
  private RpcElement readRpc(Location location, String documentation) {
    String name = reader.readName(true, false, true);

    reader.require('(');
    boolean requestStreaming = false;
    String requestWord = reader.readWord();
    String requestType;
    if ("stream".equals(requestWord)) {
      requestType = reader.readDataType();
      requestStreaming = true;
    } else {
      requestType = reader.readDataType(requestWord);
    }
    reader.require(')');

    reader.expect(reader.readWord().equals("returns"), location, "expected 'returns'");

    reader.require('(');
    boolean responseStreaming = false;
    String responseWord = reader.readWord();
    String responseType;
    if ("stream".equals(responseWord)) {
      responseType = reader.readDataType();
      responseStreaming = true;
    } else {
      responseType = reader.readDataType(responseWord);
    }
    reader.require(')');

    List<OptionElement> options = new ArrayList<>();
    if (reader.peekChar('{')) {
      while (true) {
        String rpcDocumentation = reader.readDocumentation();
        if (reader.peekChar('}')) break;

        Object declared = readDeclaration(rpcDocumentation, Context.RPC);
        if (declared instanceof OptionElement) options.add((OptionElement) declared);
      }
    } else {
      reader.require(';');
    }

    return new RpcElement(location, name, documentation, requestType, responseType,
        requestStreaming, responseStreaming, options);
  }

  enum Context {
    FILE,
    MESSAGE,
    ENUM,
    RPC,
    EXTEND,
    SERVICE;

    boolean permitsPackage() {
      return this == FILE;
    }

    boolean permitsSyntax() {
      return this == FILE;
    }

    boolean permitsEdition() {
      return this == FILE;
    }

    boolean permitsImport() {
      return this == FILE;
    }

    boolean permitsExtensions() {
      return this == MESSAGE;
    }

    boolean permitsRpc() {
      return this == SERVICE;
    }

    boolean permitsOneOf() {
      return this == MESSAGE;
    }

    boolean permitsMessage() {
      return this == FILE || this == MESSAGE;
    }

    boolean permitsService() {
      return this == FILE;
    }

    boolean permitsEnum() {
      return this == FILE || this == MESSAGE;
    }

    boolean permitsExtend() {
      return this == FILE || this == MESSAGE;
    }
  }

  /** Parse a named `.proto` schema. */
  public static ProtoFileElement parse(Location location, String data) {
    char[] chars = data.toCharArray();
    return new ProtoParser(location, chars).readProtoFile();
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code ProtoParser.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public ProtoFileElement parse(Location location, String data) {
      return ProtoParser.parse(location, data);
    }
  }
}
