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

import com.squareup.wire.schema.internal.parser.OptionElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A set of options declared on a message declaration, field declaration, enum declaration, enum
 * constant declaration, service declaration, RPC method declaration, or proto file declaration.
 * Options values may be arbitrary protocol buffer messages, but must be valid protocol buffer
 * messages.
 */
public final class Options {
  private final ProtoType optionType;
  private final List<OptionElement> optionElements;

  // Null until this options is linked.
  private List<LinkedOptionEntry> entries;

  Options(ProtoType optionType, List<OptionElement> optionElements) {
    this.optionType = optionType;
    this.optionElements = optionElements;
  }

  public List<OptionElement> elements() {
    if (entries != null) {
      List<OptionElement> result = new ArrayList<>();
      for (LinkedOptionEntry entry : entries) {
        // TODO(Benoit) this property is used to go from `Options` to `List<OptionElement>` but
        //  this doesn't take into account what has been pruned. We should consume `it.value`
        //  somehow and select the option elements we are to fetch, or not.
        result.add(entry.optionElement);
      }
      return result;
    }
    return optionElements;
  }

  public Map<ProtoMember, Object> map() {
    if (entries != null) return entriesToMap(entries);
    return Collections.emptyMap();
  }

  Options retainLinked() {
    return new Options(optionType, Collections.emptyList());
  }

  public Object get(ProtoMember protoMember) {
    if (entries == null) return null;
    for (LinkedOptionEntry entry : entries) {
      if (entry.protoMember.equals(protoMember)) return entry.value;
    }
    return null;
  }

  /**
   * Returns true if any of the options in {@code entries} matches both of the regular expressions
   * provided: its name matches the option's name and its value matches the option's value.
   */
  public boolean optionMatches(String namePattern, String valuePattern) {
    Pattern nameRegex = Pattern.compile(namePattern);
    Pattern valueRegex = Pattern.compile(valuePattern);

    for (LinkedOptionEntry entry : entries) {
      if (nameRegex.matcher(entry.protoMember.member).matches()
          && valueRegex.matcher(entry.value.toString()).matches()) {
        return true;
      }
    }
    return false;
  }

  void link(Linker linker, Location location, boolean validate) {
    List<LinkedOptionEntry> entries = Collections.emptyList();

    for (OptionElement option : optionElements) {
      List<LinkedOptionEntry> canonicalOption =
          canonicalizeOption(linker, optionType, option, validate, location);
      if (canonicalOption == null) continue;

      entries = unionEntries(linker, entries, canonicalOption);
    }
    this.entries = entries;
  }

  private List<LinkedOptionEntry> canonicalizeOption(Linker linker, ProtoType extensionType,
      OptionElement option, boolean validate, Location location) {
    Type typeOrNull = linker.getForOptions(extensionType);
    if (!(typeOrNull instanceof MessageType)) return null; // No known extensions.

    MessageType type = (MessageType) typeOrNull;
    String[] path;
    Field field = type.field(option.name);

    if (field != null) {
      // This is an option declared by descriptor.proto.
      path = new String[] {option.name};
    } else {
      // This is an option declared by an extension.
      Map<String, Field> extensionsForType = type.extensionFieldsMap();
      path = resolveFieldPath(option.name, extensionsForType.keySet());
      String namespace = linker.resolveContext();
      while (path == null && !namespace.trim().isEmpty()) {
        // If the path couldn't be resolved, attempt again by prefixing it with the package name.
        path = resolveFieldPath(namespace + "." + option.name, extensionsForType.keySet());
        // Retry with one upper level package to resolve relative paths.
        if (path == null) {
          int dot = namespace.lastIndexOf('.');
          namespace = dot == -1 ? "" : namespace.substring(0, dot);
        }
      }
      if (path == null) {
        if (validate) {
          linker.errors.add("unable to resolve option " + option.name);
        }
        return null; // Unable to find the root of this field path.
      }
      field = extensionsForType.get(path[0]);
      if (validate) {
        linker.withContext(field).validateImportForPath(location, field.location().path);
      }
    }
    linker.request(field);

    Map<ProtoMember, Object> result = new LinkedHashMap<>();
    Map<ProtoMember, Object> last = result;
    ProtoType lastProtoType = type.type();
    for (int i = 1; i < path.length; i++) {
      Map<ProtoMember, Object> nested = new LinkedHashMap<>();
      last.put(ProtoMember.get(lastProtoType, field), nested);
      lastProtoType = field.type();

      // Force members linking.
      if (lastProtoType != null) {
        linker.getForOptions(lastProtoType);
      }

      last = nested;
      field = linker.dereference(field.type(), path[i]);
      if (field == null) return null; // Unable to dereference segment.
      linker.request(field);
    }

    last.put(ProtoMember.get(lastProtoType, field),
        canonicalizeValue(linker, field.type(), field.isRepeated(), option.value));

    if (result.size() != 1) {
      throw new IllegalStateException("Check failed"); // TODO(benoit) might be safe to remove
    }
    Map.Entry<ProtoMember, Object> first = result.entrySet().iterator().next();
    return Collections.singletonList(new LinkedOptionEntry(option, first.getKey(),
        first.getValue()));
  }

  private Object canonicalizeValue(Linker linker, ProtoType context, boolean isRepeated,
      Object value) {
    if (value instanceof OptionElement) {
      OptionElement element = (OptionElement) value;
      Map<ProtoMember, Object> result = new LinkedHashMap<>();
      Field field = linker.dereference(context, element.name);
      if (field == null) {
        linker.errors.add("unable to resolve option " + element.name + " on " + context);
      } else {
        ProtoMember protoMember = ProtoMember.get(context, field);
        result.put(protoMember,
            canonicalizeValue(linker, field.type(), field.isRepeated(), element.value));
      }
      return coerceValueForField(context, result, isRepeated);
    }

    if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      if (context.isMap) {
        // Map fields are defined with two optional entries: `key` and 'value'.
        Object mapFieldKeyAsString = map.get("key");
        Object mapFieldValueAsString = map.get("value");
        Object mapFieldKey = mapFieldKeyAsString == null ? null
            : canonicalizeValue(linker, context.keyType, false, mapFieldKeyAsString);
        Object mapFieldValue = mapFieldValueAsString == null ? null
            : canonicalizeValue(linker, context.valueType, false, mapFieldValueAsString);
        Map<Object, Object> mapResult = new LinkedHashMap<>();
        mapResult.put(mapFieldKey, mapFieldValue);
        return coerceValueForField(context, mapResult, isRepeated);
      } else {
        Map<ProtoMember, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          String name = (String) entry.getKey();
          Field field = linker.dereference(context, name);
          if (field == null) {
            linker.errors.add("unable to resolve option " + name + " on " + context);
          } else {
            ProtoMember protoMember = ProtoMember.get(context, field);
            result.put(protoMember,
                canonicalizeValue(linker, field.type(), field.isRepeated(), entry.getValue()));
          }
        }
        return coerceValueForField(context, result, isRepeated);
      }
    }

    if (value instanceof List) {
      List<Object> result = new ArrayList<>();
      for (Object element : (List<?>) value) {
        Object canonical = canonicalizeValue(linker, context, isRepeated, element);
        result.addAll((List<Object>) canonical);
      }
      return coerceValueForField(context, result, isRepeated);
    }

    if (value instanceof String) {
      validateOptionValue(linker, context, (String) value);
      return coerceValueForField(context, value, isRepeated);
    }

    if (value instanceof OptionElement.OptionPrimitive) {
      return canonicalizeValue(linker, context, isRepeated,
          ((OptionElement.OptionPrimitive) value).value);
    }

    throw new IllegalArgumentException("Unexpected option value: " + value);
  }

  private void validateOptionValue(Linker linker, ProtoType context, String value) {
    if (!LiteralValidation.isValidLiteral(linker, context, value)) {
      linker.errors.add("invalid option value \"" + value + "\" for " + context);
    }
  }

  private Object coerceValueForField(ProtoType context, Object value, boolean isRepeated) {
    if (isRepeated || context.isMap) {
      return value instanceof List ? value : Collections.singletonList(value);
    }
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      // Kotlin's List.single(): fail loudly on empty or multi-element singular options.
      if (list.isEmpty()) throw new NoSuchElementException("List is empty.");
      if (list.size() > 1) {
        throw new IllegalArgumentException("List has more than one element.");
      }
      return list.get(0);
    }
    return value;
  }

  /** Combine values for the same key, resolving conflicts based on their type. */
  private Object unionAny(Linker linker, Object a, Object b) {
    if (a instanceof List) {
      List<Object> result = new ArrayList<>((List<?>) a);
      result.addAll((List<?>) b);
      return result;
    }

    if (a instanceof Map) {
      return unionMaps(linker, (Map<ProtoMember, Object>) a, (Map<ProtoMember, Object>) b);
    }

    linker.errors.add("conflicting options: " + a + ", " + b);
    return a; // Just return any placeholder.
  }

  private List<LinkedOptionEntry> unionEntries(Linker linker, List<LinkedOptionEntry> a,
      List<LinkedOptionEntry> b) {
    Map<ProtoMember, Object> aMap = entriesToMap(a);
    Map<ProtoMember, Object> bMap = entriesToMap(b);

    Map<ProtoMember, Object> valuesMap = new LinkedHashMap<>(aMap);
    for (Map.Entry<ProtoMember, Object> entry : bMap.entrySet()) {
      Object bValue = entry.getValue();
      Object aValue = valuesMap.get(entry.getKey());
      valuesMap.put(entry.getKey(),
          aValue != null ? unionAny(linker, aValue, bValue) : bValue);
    }

    // Union of (optionElement, protoMember) pairs, first-list order, then new pairs from b.
    Set<Map.Entry<OptionElement, ProtoMember>> seen = new LinkedHashSet<>();
    List<LinkedOptionEntry> combined = new ArrayList<>();
    for (LinkedOptionEntry entry : a) {
      combined.add(entry);
      seen.add(new java.util.AbstractMap.SimpleImmutableEntry<>(entry.optionElement,
          entry.protoMember));
    }
    for (LinkedOptionEntry entry : b) {
      if (seen.add(new java.util.AbstractMap.SimpleImmutableEntry<>(entry.optionElement,
          entry.protoMember))) {
        combined.add(entry);
      }
    }
    List<LinkedOptionEntry> result = new ArrayList<>();
    for (LinkedOptionEntry entry : combined) {
      result.add(new LinkedOptionEntry(entry.optionElement, entry.protoMember,
          valuesMap.get(entry.protoMember)));
    }
    return result;
  }

  private Map<ProtoMember, Object> unionMaps(Linker linker, Map<ProtoMember, Object> a,
      Map<ProtoMember, Object> b) {
    Map<ProtoMember, Object> result = new LinkedHashMap<>(a);
    for (Map.Entry<ProtoMember, Object> entry : b.entrySet()) {
      Object bValue = entry.getValue();
      Object aValue = result.get(entry.getKey());
      result.put(entry.getKey(),
          aValue != null ? unionAny(linker, aValue, bValue) : bValue);
    }
    return result;
  }

  public Multimap<ProtoType, ProtoMember> fields() {
    return fields(new PruningRules.Builder().build());
  }

  public Multimap<ProtoType, ProtoMember> fields(PruningRules pruningRules) {
    Map<ProtoType, Collection<ProtoMember>> sink = new LinkedHashMap<>();
    gatherFields(sink, optionType, map(), pruningRules);
    return Multimap.toMultimap(sink);
  }

  private void gatherFields(Map<ProtoType, Collection<ProtoMember>> sink, ProtoType type,
      Object o, PruningRules pruningRules) {
    if (o instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) o).entrySet()) {
        Object key = entry.getKey();
        if (!(key instanceof ProtoMember)) {
          // When the key isn't a `ProtoMember`, this key/value pair is a inlined value of a map
          // field. We don't need to track the key type in map fields for they are always of
          // scalar types. We however have to check the value type.
          gatherFields(sink, type, entry.getValue(), pruningRules);
          continue;
        }
        ProtoMember protoMember = (ProtoMember) key;
        if (pruningRules.prunes(protoMember)) continue;
        sink.computeIfAbsent(type, k -> new ArrayList<>()).add(protoMember);
        gatherFields(sink, protoMember.type, entry.getValue(), pruningRules);
      }
    } else if (o instanceof List) {
      for (Object e : (List<?>) o) {
        gatherFields(sink, type, e, pruningRules);
      }
    }
  }

  Options retainAll(Schema schema, MarkSet markSet) {
    if (entries == null || entries.isEmpty()) return this; // Nothing to prune.

    Options result = new Options(optionType, optionElements);

    Map<ProtoMember, Object> map = (Map<ProtoMember, Object>) retainAll(schema, markSet,
        optionType, map());
    if (map == null) map = Collections.emptyMap();

    List<LinkedOptionEntry> retainedEntries = new ArrayList<>();
    for (LinkedOptionEntry entry : entries) {
      if (map.containsKey(entry.protoMember)) {
        retainedEntries.add(new LinkedOptionEntry(entry.optionElement, entry.protoMember,
            map.get(entry.protoMember)));
      }
    }
    result.entries = retainedEntries;

    return result;
  }

  /** Returns an object of the same type as {@code o}, or null if it is not retained. */
  private Object retainAll(Schema schema, MarkSet markSet, ProtoType type, Object o) {
    if (o == null) return null;

    if (o instanceof Map) {
      Map<ProtoMember, Object> map = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) o).entrySet()) {
        Object key = entry.getKey();
        if (!(key instanceof ProtoMember)) {
          // When the key isn't a `ProtoMember`, this key/value pair is a inlined value of a map
          // field.
          Object retainedValue = retainAll(schema, markSet, type, entry.getValue());
          // if `retainedValue` is a map, its value represents an inline message, and we need to
          // mark the proto member.
          if (retainedValue instanceof Map) {
            for (Map.Entry<?, ?> retained : ((Map<?, ?>) retainedValue).entrySet()) {
              map.put((ProtoMember) retained.getKey(), retained.getValue());
            }
          }
          continue;
        }
        ProtoMember protoMember = (ProtoMember) key;
        boolean isCoreMemberOfGoogleProtobuf =
            Options.isGoogleProtobufOptionType(protoMember.type)
                && !schema.isExtensionField(protoMember);
        if (!markSet.contains(protoMember) && !isCoreMemberOfGoogleProtobuf) {
          continue; // Prune this field.
        }

        Field field = schema.getField(protoMember);
        Object retainedValue = retainAll(schema, markSet, field.type(), entry.getValue());
        if (retainedValue != null) {
          map.put(protoMember, retainedValue); // This retained field is non-empty.
        } else if (isCoreMemberOfGoogleProtobuf) {
          map.put(protoMember, entry.getValue());
        }
      }
      return map.isEmpty() ? null : map;
    }

    if (o instanceof List) {
      List<Object> list = new ArrayList<>();
      for (Object value : (List<?>) o) {
        Object retainedValue = retainAll(schema, markSet, type, value);
        if (retainedValue != null) {
          list.add(retainedValue); // This retained value is non-empty.
        }
      }
      return list.isEmpty() ? null : list;
    }

    if (!markSet.contains(type)) return null; // Prune this type.

    return o;
  }

  private static Map<ProtoMember, Object> entriesToMap(List<LinkedOptionEntry> entries) {
    Map<ProtoMember, Object> result = new LinkedHashMap<>();
    for (LinkedOptionEntry entry : entries) {
      result.put(entry.protoMember, entry.value);
    }
    return result;
  }

  public static final ProtoType FILE_OPTIONS = ProtoType.get("google.protobuf.FileOptions");
  private static final ProtoType FILE_OPTIONS_VARIANT = ProtoType.get(".google.protobuf.FileOptions");

  public static final ProtoType MESSAGE_OPTIONS = ProtoType.get("google.protobuf.MessageOptions");
  private static final ProtoType MESSAGE_OPTIONS_VARIANT =
      ProtoType.get(".google.protobuf.MessageOptions");

  public static final ProtoType FIELD_OPTIONS = ProtoType.get("google.protobuf.FieldOptions");
  private static final ProtoType FIELD_OPTIONS_VARIANT = ProtoType.get(".google.protobuf.FieldOptions");

  public static final ProtoType ONEOF_OPTIONS = ProtoType.get("google.protobuf.OneofOptions");
  private static final ProtoType ONEOF_OPTIONS_VARIANT = ProtoType.get(".google.protobuf.OneofOptions");

  public static final ProtoType ENUM_OPTIONS = ProtoType.get("google.protobuf.EnumOptions");
  private static final ProtoType ENUM_OPTIONS_VARIANT = ProtoType.get(".google.protobuf.EnumOptions");

  public static final ProtoType ENUM_VALUE_OPTIONS =
      ProtoType.get("google.protobuf.EnumValueOptions");
  private static final ProtoType ENUM_VALUE_OPTIONS_VARIANT =
      ProtoType.get(".google.protobuf.EnumValueOptions");

  public static final ProtoType SERVICE_OPTIONS = ProtoType.get("google.protobuf.ServiceOptions");
  private static final ProtoType SERVICE_OPTIONS_VARIANT =
      ProtoType.get(".google.protobuf.ServiceOptions");

  public static final ProtoType METHOD_OPTIONS = ProtoType.get("google.protobuf.MethodOptions");
  private static final ProtoType METHOD_OPTIONS_VARIANT =
      ProtoType.get(".google.protobuf.MethodOptions");

  public static final ProtoType EXTENSION_RANGE_OPTIONS =
      ProtoType.get("google.protobuf.ExtensionRangeOptions");
  private static final ProtoType EXTENSION_RANGE_OPTIONS_VARIANT =
      ProtoType.get(".google.protobuf.ExtensionRangeOptions");

  // Protobuf allows leading dots when referencing a type. We add the variants to make sure our
  // equality check match them too when we need to know that a ProtoType is a Protobuf option.
  static final ProtoType[] GOOGLE_PROTOBUF_OPTION_TYPES = {
      FILE_OPTIONS,
      MESSAGE_OPTIONS,
      FIELD_OPTIONS,
      ONEOF_OPTIONS,
      ENUM_OPTIONS,
      ENUM_VALUE_OPTIONS,
      SERVICE_OPTIONS,
      METHOD_OPTIONS,
      EXTENSION_RANGE_OPTIONS,
      FILE_OPTIONS_VARIANT,
      MESSAGE_OPTIONS_VARIANT,
      FIELD_OPTIONS_VARIANT,
      ONEOF_OPTIONS_VARIANT,
      ENUM_OPTIONS_VARIANT,
      ENUM_VALUE_OPTIONS_VARIANT,
      SERVICE_OPTIONS_VARIANT,
      METHOD_OPTIONS_VARIANT,
      EXTENSION_RANGE_OPTIONS_VARIANT,
  };

  static boolean isGoogleProtobufOptionType(ProtoType type) {
    for (ProtoType optionType : GOOGLE_PROTOBUF_OPTION_TYPES) {
      if (optionType.equals(type)) return true;
    }
    return false;
  }

  /**
   * Given a path like {@code a.b.c.d} and a set of paths like {@code {a.b.c, a.f.g, h.j}}, this
   * returns the original path split on dots such that the first element is in the set. For the
   * above example it would return the array {@code [a.b.c, d]}.
   *
   * <p>Typically, the input path is a package name like {@code a.b}, followed by a dot and a
   * sequence of field names. The first field name is an extension field; subsequent field names
   * make a path within that extension.
   *
   * <p>https://developers.google.com/protocol-buffers/docs/overview?hl=en#packages_and_name_resolution
   * Names can be prefixed with a {@code .} when the search should start from the outermost scope.
   *
   * <p>Note that a single input may yield multiple possible answers, such as when package names
   * and field names collide. This method prefers shorter package names though that is an
   * implementation detail.
   */
  private static final java.util.regex.Pattern DOT_PATTERN = java.util.regex.Pattern.compile("\\.");

  public static String[] resolveFieldPath(String name, Set<String> fullyQualifiedNames) {
    // Try to resolve a local name.
    int pos = 0;
    String chompedName = name.startsWith(".") ? name.substring(1) : name;
    while (pos < chompedName.length()) {
      pos = chompedName.indexOf('.', pos);
      if (pos == -1) pos = chompedName.length();
      String candidate = chompedName.substring(0, pos);
      if (fullyQualifiedNames.contains(candidate)) {
        String remainder = chompedName.substring(pos);
        String[] path = remainder.isEmpty()
            ? new String[] { "" }
            : DOT_PATTERN.split(remainder, -1);
        path[0] = chompedName.substring(0, pos);
        return path;
      }
      pos++;
    }
    return null;
  }
}
