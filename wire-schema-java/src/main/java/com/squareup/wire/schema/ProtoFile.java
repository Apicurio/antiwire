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

import com.squareup.wire.Syntax;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProtoFile {
  static final ProtoMember JAVA_PACKAGE = ProtoMember.get(Options.FILE_OPTIONS, "java_package");
  static final ProtoMember WIRE_PACKAGE =
      ProtoMember.get(Options.FILE_OPTIONS, "wire.wire_package");

  final Location location;
  final List<String> imports;
  final List<String> publicImports;
  final List<String> weakImports;
  final String packageName;
  final List<Type> types;
  final List<Service> services;
  final List<Extend> extendList;
  final Options options;
  final Syntax syntax;

  private Object javaPackage;

  private Object wirePackage;

  ProtoFile(Location location, List<String> imports, List<String> publicImports,
      List<String> weakImports, String packageName, List<Type> types, List<Service> services,
      List<Extend> extendList, Options options, Syntax syntax) {
    this.location = location;
    this.imports = imports;
    this.publicImports = publicImports;
    this.weakImports = weakImports;
    this.packageName = packageName;
    this.types = types;
    this.services = services;
    this.extendList = extendList;
    this.options = options;
    this.syntax = syntax;
  }

  public Location location() {
    return location;
  }

  public List<String> imports() {
    return imports;
  }

  public List<String> publicImports() {
    return publicImports;
  }

  public List<String> weakImports() {
    return weakImports;
  }

  public String packageName() {
    return packageName;
  }

  public List<Type> types() {
    return types;
  }

  public List<Service> services() {
    return services;
  }

  public List<Extend> extendList() {
    return extendList;
  }

  public Options options() {
    return options;
  }

  /** The syntax as declared in the source file; null means proto2. */
  public Syntax syntax() {
    return syntax;
  }

  public ProtoFileElement toElement() {
    return new ProtoFileElement(location, packageName, syntax, imports, publicImports,
        weakImports, Type.toElements(types), Service.toElements(services),
        Extend.toElements(extendList), options.elements());
  }

  /**
   * Returns the name of this proto file, like {@code simple_message} for
   * {@code squareup/protos/person/simple_message.proto}.
   */
  public String name() {
    String result = location.path;

    int slashIndex = result.lastIndexOf('/');
    if (slashIndex != -1) {
      result = result.substring(slashIndex + 1);
    }

    if (result.endsWith(".proto")) {
      result = result.substring(0, result.length() - ".proto".length());
    }

    return result;
  }

  /** Returns all types and subtypes which are found in the proto file. */
  public List<Type> typesAndNestedTypes() {
    List<Type> typesAndNestedTypes = new ArrayList<>();
    for (Type type : types) {
      typesAndNestedTypes.addAll(type.typesAndNestedTypes());
    }
    return typesAndNestedTypes;
  }

  public String javaPackage() {
    return javaPackage != null ? javaPackage.toString() : null;
  }

  public String wirePackage() {
    return wirePackage != null ? wirePackage.toString() : null;
  }

  /** Returns a new proto file that omits types, services, extensions, and options not pruned. */
  ProtoFile retainAll(Schema schema, MarkSet markSet) {
    List<Type> retainedTypes = new ArrayList<>();
    for (Type type : types) {
      Type retained = type.retainAll(schema, markSet);
      if (retained != null) retainedTypes.add(retained);
    }

    List<Service> retainedServices = new ArrayList<>();
    for (Service service : services) {
      Service retained = service.retainAll(schema, markSet);
      if (retained != null) retainedServices.add(retained);
    }

    List<Extend> retainedExtends = new ArrayList<>();
    for (Extend extend : extendList) {
      Extend retained = extend.retainAll(schema, markSet);
      if (retained != null) retainedExtends.add(retained);
    }

    Options retainedOptions = options.retainAll(schema, markSet);

    ProtoFile result = new ProtoFile(location, imports, publicImports, weakImports, packageName,
        retainedTypes, retainedServices, retainedExtends, retainedOptions, syntax);
    result.javaPackage = javaPackage;
    result.wirePackage = wirePackage;
    return result;
  }

  /** Return a copy of this file with only the marked types. */
  ProtoFile retainLinked(Set<ProtoType> linkedTypes, Set<Field> linkedFields) {
    List<Type> retainedTypes = new ArrayList<>();
    for (Type type : types) {
      Type retained = type.retainLinked(linkedTypes, linkedFields);
      if (retained != null) retainedTypes.add(retained);
    }
    List<Extend> retainedExtends = new ArrayList<>();
    for (Extend extend : extendList) {
      Extend retained = extend.retainLinked(linkedFields);
      if (retained != null) retainedExtends.add(retained);
    }

    // Other .proto files can't link to our services so strip them unconditionally.
    List<Service> retainedServices = Collections.emptyList();

    Options retainedOptions = options.retainLinked();

    ProtoFile result = new ProtoFile(location, imports, publicImports, weakImports, packageName,
        retainedTypes, retainedServices, retainedExtends, retainedOptions, syntax);
    result.javaPackage = javaPackage;
    result.wirePackage = wirePackage;
    return result;
  }

  /** Returns all types referenced by types and subtypes defined in the proto file. */
  private List<ProtoType> referencedTypes() {
    List<Type> allTypes = typesAndNestedTypes();
    List<MessageType> messages = new ArrayList<>();
    List<EnumType> enums = new ArrayList<>();
    for (Type type : allTypes) {
      if (type instanceof MessageType) messages.add((MessageType) type);
      else if (type instanceof EnumType) enums.add((EnumType) type);
    }

    List<ProtoType> result = new ArrayList<>();
    for (MessageType message : messages) {
      for (Field field : message.fieldsAndOneOfFields()) {
        if (field.type() != null) result.add(field.type());
      }
    }

    for (Service service : services) {
      for (Rpc rpc : service.rpcs()) {
        if (rpc.requestType() != null) result.add(rpc.requestType());
        if (rpc.responseType() != null) result.add(rpc.responseType());
      }
    }

    for (Extend extend : extendList) {
      for (Field field : extend.fields()) {
        if (field.type() != null) result.add(field.type());
      }
    }
    for (Type type : types) {
      for (Extend extend : type.nestedExtendList()) {
        for (Field field : extend.fields()) {
          if (field.type() != null) result.add(field.type());
        }
      }
    }

    List<Options> allOptions = new ArrayList<>();
    allOptions.add(options); // file options
    for (MessageType message : messages) {
      allOptions.add(message.options()); // message options
      for (Field field : message.fields()) {
        allOptions.add(field.options()); // field options
      }
      for (OneOf oneOf : message.oneOfs()) {
        for (Field field : oneOf.fields()) {
          allOptions.add(field.options()); // one-of field options
        }
        allOptions.add(oneOf.options()); // one-of options
      }
    }
    for (EnumType enumType : enums) {
      allOptions.add(enumType.options()); // enum options
      for (EnumConstant constant : enumType.constants()) {
        allOptions.add(constant.options()); // enum value options
      }
    }
    for (Service service : services) {
      allOptions.add(service.options()); // service options
      for (Rpc rpc : service.rpcs()) {
        allOptions.add(rpc.options()); // method options
      }
    }
    for (Options opts : allOptions) {
      result.addAll(opts.fields().asMap().keySet());
    }

    // distinct() preserving first-seen order.
    List<ProtoType> distinct = new ArrayList<>();
    Set<ProtoType> seen = new LinkedHashSet<>();
    for (ProtoType protoType : result) {
      if (seen.add(protoType)) distinct.add(protoType);
    }
    return distinct;
  }

  /** Returns a new proto file that omits unnecessary imports. */
  ProtoFile retainImports(Schema schema) {
    List<Type> referenced = new ArrayList<>();
    for (ProtoType protoType : referencedTypes()) {
      if (protoType.isMap) {
        // We only need to retain the value type; map keys' types can only be scalar types.
        Type type = schema.getType(protoType.valueType);
        if (type != null) referenced.add(type);
      } else {
        Type type = schema.getType(protoType);
        if (type != null) referenced.add(type);
      }
    }

    List<Location> typeLocations = new ArrayList<>();
    for (Type type : referenced) {
      typeLocations.add(type.location());
    }

    List<Location> extensionLocations = new ArrayList<>();
    for (Type type : referenced) {
      if (type instanceof MessageType) {
        for (Field field : ((MessageType) type).extensionFields()) {
          extensionLocations.add(field.location());
        }
      }
    }

    Set<String> referencedImports = new LinkedHashSet<>();
    for (Location typeLocation : typeLocations) referencedImports.add(typeLocation.path);
    for (Location extensionLocation : extensionLocations) referencedImports.add(extensionLocation.path);

    List<String> retainedImports = new ArrayList<>();
    for (String path : imports) {
      if (referencedImports.contains(path)) retainedImports.add(path);
    }

    Set<String> nonEmptyProtoFilesInSchema = new LinkedHashSet<>();
    for (ProtoFile protoFile : schema.protoFiles()) {
      if (!protoFile.types.isEmpty() || !protoFile.services.isEmpty()
          || !protoFile.extendList.isEmpty()) {
        nonEmptyProtoFilesInSchema.add(protoFile.location.path);
      }
    }

    List<String> retainedPublicImports = new ArrayList<>();
    for (String path : publicImports) {
      if (nonEmptyProtoFilesInSchema.contains(path)) retainedPublicImports.add(path);
    }

    List<String> retainedWeakImports = new ArrayList<>();
    for (String path : weakImports) {
      if (referencedImports.contains(path)) retainedWeakImports.add(path);
    }

    if (imports.size() != retainedImports.size()
        || publicImports.size() != retainedPublicImports.size()
        || weakImports.size() != retainedWeakImports.size()) {
      ProtoFile result = new ProtoFile(location, retainedImports, retainedPublicImports,
          retainedWeakImports, packageName, types, services, extendList, options, syntax);
      result.javaPackage = javaPackage;
      result.wirePackage = wirePackage;
      return result;
    }
    return this;
  }

  void linkOptions(Linker linker, boolean validate) {
    options.link(linker, location, validate);
    javaPackage = options.get(JAVA_PACKAGE);
    wirePackage = options.get(WIRE_PACKAGE);
  }

  @Override public String toString() {
    return location.path;
  }

  public String toSchema() {
    return toElement().toSchema();
  }

  public static ProtoFile get(ProtoFileElement protoFileElement) {
    String packageName = protoFileElement.packageName;

    Syntax syntax = protoFileElement.syntax == null ? Syntax.PROTO_2 : protoFileElement.syntax;
    List<Type> types = Type.fromElements(packageName, protoFileElement.types, syntax);

    List<Service> services = Service.fromElements(packageName, protoFileElement.services);

    List<String> namespaces = packageName == null
        ? Collections.emptyList()
        : Collections.singletonList(packageName);
    List<Extend> wireExtends = Extend.fromElements(namespaces,
        protoFileElement.extendDeclarations);

    Options options = new Options(Options.FILE_OPTIONS, protoFileElement.options);

    return new ProtoFile(protoFileElement.location, protoFileElement.imports,
        protoFileElement.publicImports, protoFileElement.weakImports, packageName, types,
        services, wireExtends, options, protoFileElement.syntax);
  }
}
