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
package com.squareup.wire.schema;

import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class Field {
  /**
   * The namespaces in which the field is defined. For top-level extensions in a file that has no
   * package declaration, this may be empty. For normal fields and extensions nested inside a
   * message, the first entry will always be the package name, which might be the empty string if
   * defined in a file that has no package declaration. Subsequent entries will be the names of
   * enclosing messages, outer-most to inner-most.
   */
  final List<String> namespaces;

  final Location location;

  /** May be null for proto3 fields, one-of's, or maps. */
  final Label label;

  final String name;

  final String documentation;

  final int tag;

  final String defaultValue;

  private String elementType;

  final Options options;

  final boolean isExtension;

  final boolean isOneOf;

  final String declaredJsonName;

  // Null until this field is linked.
  private ProtoType type;

  // Null until this field is linked.
  private Object deprecated;

  // False until this field is linked.
  private boolean isRedacted;

  // Null until this field is linked.
  private EncodeMode encodeMode;

  // Null until this field is linked.
  private String jsonName;

  public Field(List<String> namespaces, Location location, Label label, String name,
      String documentation, int tag, String defaultValue, String elementType, Options options,
      boolean isExtension, boolean isOneOf, String declaredJsonName) {
    this.namespaces = namespaces;
    this.location = location;
    this.label = label;
    this.name = name;
    this.documentation = documentation;
    this.tag = tag;
    this.defaultValue = defaultValue;
    this.elementType = elementType;
    this.options = options;
    this.isExtension = isExtension;
    this.isOneOf = isOneOf;
    this.declaredJsonName = declaredJsonName;
  }

  public List<String> getNamespaces() {
    return namespaces;
  }

  public Location getLocation() {
    return location;
  }

  /** May be null for proto3 fields, one-of's, or maps. */
  public Label getLabel() {
    return label;
  }

  public String getName() {
    return name;
  }

  public String getDocumentation() {
    return documentation;
  }

  public int getTag() {
    return tag;
  }

  public String getDefault() {
    return defaultValue;
  }

  String elementType() {
    return elementType;
  }

  public Options getOptions() {
    return options;
  }

  public boolean isExtension() {
    return isExtension;
  }

  public boolean isOneOf() {
    return isOneOf;
  }

  public String getDeclaredJsonName() {
    return declaredJsonName;
  }

  /** Null until this field is linked. */
  public ProtoType getType() {
    return type;
  }

  public boolean isRepeated() {
    return label == Label.REPEATED;
  }

  public boolean isRequired() {
    return encodeMode == EncodeMode.REQUIRED;
  }

  /** Null until this field is linked. */
  public EncodeMode getEncodeMode() {
    return encodeMode;
  }

  /**
   * Returns this field's name, prefixed with its namespaces. Uniquely identifies extension
   * fields, such as in options.
   */
  public String getQualifiedName() {
    String joined = String.join(".", namespaces);
    String prefix = joined.startsWith(".") ? joined.substring(1) : joined;
    return prefix.isEmpty() ? name : prefix + "." + name;
  }

  /**
   * Returns the package in which this field is defined. If the file that defined this field has
   * no package declaration, returns the empty string.
   */
  public String getPackageName() {
    return namespaces.isEmpty() ? "" : namespaces.get(0);
  }

  public boolean isDeprecated() {
    return "true".equals(deprecated);
  }

  /** False until this field is linked. */
  public boolean isRedacted() {
    return isRedacted;
  }

  public boolean isPacked() {
    return encodeMode == EncodeMode.PACKED;
  }

  public boolean getUseArray() {
    return "true".equals(options.get(WIRE_USE_ARRAY));
  }

  /** Null until this field is linked. */
  public String getJsonName() {
    return jsonName;
  }

  private boolean isPackable(Linker linker, ProtoType type) {
    return !type.equals(ProtoType.STRING)
        && !type.equals(ProtoType.BYTES)
        && !(linker.get(type) instanceof MessageType);
  }

  public void link(Linker linker) {
    type = linker.withContext(this).resolveType(elementType);
    if (ProtoType.BYTES.equals(type) && !elementType.equals("bytes")) {
      // The type has been opaqued, we update its proto definition as well.
      elementType = "bytes";
    }
  }

  public void linkOptions(Linker linker, SyntaxRules syntaxRules, boolean validate) {
    Linker scoped = linker.withContext(this);
    options.link(scoped, location, validate);
    deprecated = options.get(DEPRECATED);
    Object packed = options.get(PACKED);
    if (packed == null && syntaxRules.isPackedByDefault(type, label)) {
      packed = OptionElement.PACKED_OPTION_ELEMENT.getValue();
    }
    // We allow any package name to be used as long as it ends with '.redacted'.
    isRedacted = options.optionMatches(".*\\.redacted", "true");

    encodeMode = syntaxRules.getEncodeMode(type, label, "true".equals(packed), isOneOf);
    jsonName = syntaxRules.jsonName(name, declaredJsonName);
  }

  public void validate(Linker linker, SyntaxRules syntaxRules) {
    Linker scoped = linker.withContext(this);
    if (isPacked() && !isPackable(scoped, type)) {
      scoped.getErrors().add("packed=true not permitted on " + type);
    }
    if (getUseArray() && !isPacked()) {
      scoped.getErrors().add("wire.use_array=true only permitted on packed fields");
    }
    if (getUseArray() && (type == null || !type.isScalar())) {
      scoped.getErrors().add("wire.use_array=true only permitted on scalar fields");
    }
    if (isExtension) {
      if (isRequired()) {
        scoped.getErrors().add("extension fields cannot be required");
      }
      if (type.isMap()) {
        scoped.getErrors().add("extension fields cannot be a map");
      }
    }
    syntaxRules.validateDefaultValue(defaultValue != null, scoped.getErrors());
    validateDefaultValue(scoped);
    if (type.isMap()) {
      Type valueType = scoped.get(type.getValueType());
      if (valueType instanceof EnumType
          && ((EnumType) valueType).getConstants().get(0).getTag() != 0) {
        scoped.getErrors().add("enum value in map must define 0 as the first value");
      }
    }
    scoped.validateImportForType(location, type);
  }

  private void validateDefaultValue(Linker linker) {
    if (defaultValue == null) return;

    if (!LiteralValidation.isValidLiteral(linker, type, defaultValue)) {
      linker.getErrors().add("invalid default value \"" + defaultValue + "\" for " + type);
    }
  }

  public Field retainAll(Schema schema, MarkSet markSet, ProtoType enclosingType) {
    // TODO(jwilson): perform this transformation in the Linker.
    ProtoType type = this.type;
    if (type == null) return null;

    // For map types only the value can participate in pruning as the key will always be scalar.
    if (type.isMap() && !markSet.contains(type.getValueType())) return null;

    if (!markSet.contains(type)) return null;

    String memberName = isExtension ? getQualifiedName() : name;
    ProtoMember protoMember = ProtoMember.get(enclosingType, memberName);

    if (!markSet.contains(protoMember)
        && !(Options.isGoogleProtobufOptionType(enclosingType) && !isExtension)) {
      return null;
    }

    return withOptions(options.retainAll(schema, markSet));
  }

  /** Returns a copy of this whose options is {@code options}. */
  private Field withOptions(Options options) {
    Field result = new Field(namespaces, location, label, name, documentation, tag, defaultValue,
        elementType, options, isExtension, isOneOf, declaredJsonName);
    result.type = type;
    result.deprecated = deprecated;
    result.encodeMode = encodeMode;
    result.isRedacted = isRedacted;
    result.jsonName = jsonName;
    return result;
  }

  @Override public String toString() {
    return name;
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Field)) return false;
    Field that = (Field) other;
    return namespaces.equals(that.namespaces)
        && location.equals(that.location)
        && label == that.label
        && name.equals(that.name)
        && documentation.equals(that.documentation)
        && tag == that.tag
        && Objects.equals(defaultValue, that.defaultValue)
        && elementType.equals(that.elementType)
        && Objects.equals(options, that.options)
        && isExtension == that.isExtension
        && isOneOf == that.isOneOf
        && Objects.equals(declaredJsonName, that.declaredJsonName);
  }

  @Override public int hashCode() {
    int result = namespaces.hashCode();
    result = 31 * result + location.hashCode();
    result = 31 * result + (label == null ? 0 : label.hashCode());
    result = 31 * result + name.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + tag;
    result = 31 * result + Objects.hashCode(defaultValue);
    result = 31 * result + elementType.hashCode();
    result = 31 * result + Objects.hashCode(options);
    result = 31 * result + (isExtension ? 1 : 0);
    result = 31 * result + (isOneOf ? 1 : 0);
    result = 31 * result + Objects.hashCode(declaredJsonName);
    return result;
  }

  public enum Label {
    OPTIONAL,
    REQUIRED,
    REPEATED,

    /** Indicates the field is a member of a {@code oneof} block. */
    ONE_OF,
  }

  public enum EncodeMode {
    /** Optional, or OneOf from proto2. */
    NULL_IF_ABSENT,

    /** Required from proto2. */
    REQUIRED,

    /** Non-repeated fields in proto3. Identity can be {@code 0}, {@code false}, {@code ""}, or {@code null}. */
    OMIT_IDENTITY,

    /** List. */
    REPEATED,

    /** Packed encoded list. */
    PACKED,

    /** Map. */
    MAP,
  }

  static final ProtoMember DEPRECATED = ProtoMember.get(Options.FIELD_OPTIONS, "deprecated");
  static final ProtoMember PACKED = ProtoMember.get(Options.FIELD_OPTIONS, "packed");
  static final ProtoMember WIRE_USE_ARRAY =
      ProtoMember.get(Options.FIELD_OPTIONS, "wire.use_array");

  public static List<Field> fromElements(List<String> namespaces,
      List<FieldElement> fieldElements, boolean extension, boolean oneOf) {
    List<Field> result = new ArrayList<>();
    for (FieldElement element : fieldElements) {
      result.add(new Field(namespaces, element.getLocation(), element.getLabel(), element.getName(),
          element.getDocumentation(), element.getTag(), element.getDefaultValue(), element.getType(),
          new Options(Options.FIELD_OPTIONS, element.getOptions()), extension, oneOf,
          element.getJsonName()));
    }
    return result;
  }

  public static List<FieldElement> toElements(List<Field> fields) {
    List<FieldElement> result = new ArrayList<>();
    for (Field field : fields) {
      result.add(new FieldElement(field.location, field.label, field.elementType, field.name,
          field.defaultValue, field.declaredJsonName, field.tag, field.documentation,
          field.options.getElements()));
    }
    return result;
  }

  public static List<Field> retainLinked(List<Field> fields) {
    List<Field> result = new ArrayList<>();
    for (Field field : fields) {
      // If the type is non-null, then the field has been linked.
      if (field.type != null) {
        result.add(field.withOptions(field.options.retainLinked()));
      }
    }
    return result;
  }

  public static List<Field> retainAll(Schema schema, MarkSet markSet, ProtoType enclosingType,
      Collection<Field> fields) {
    List<Field> result = new ArrayList<>();
    for (Field field : fields) {
      Field retained = field.retainAll(schema, markSet, enclosingType);
      if (retained != null) result.add(retained);
    }
    return result;
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code Field.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public List<Field> fromElements(List<String> namespaces, List<FieldElement> fieldElements, boolean extension, boolean oneOf) {
      return Field.fromElements(namespaces, fieldElements, extension, oneOf);
    }

    public List<Field> retainAll(Schema schema, MarkSet markSet, ProtoType enclosingType, Collection<Field> fields) {
      return Field.retainAll(schema, markSet, enclosingType, fields);
    }

    public List<Field> retainLinked(List<Field> fields) {
      return Field.retainLinked(fields);
    }

    public List<FieldElement> toElements(List<Field> fields) {
      return Field.toElements(fields);
    }
  }
}
