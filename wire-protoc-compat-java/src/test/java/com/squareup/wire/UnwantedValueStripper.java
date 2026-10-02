/*
 * Copyright (C) 2021 Square, Inc.
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

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import java.util.ArrayList;
import java.util.List;

/**
 * Translation of upstream wire-protoc-compatibility-tests UnwantedValueStripper.kt: the
 * constructor default {@code clearJsonName = false} becomes the no-argument constructor.
 * Behavior is byte-faithful to upstream; see this module's UPSTREAM-TEST-ADAPTATIONS.md.
 */
class UnwantedValueStripper {
  final boolean clearJsonName;

  UnwantedValueStripper() {
    this(false);
  }

  UnwantedValueStripper(boolean clearJsonName) {
    this.clearJsonName = clearJsonName;
  }

  /**
   * TODO: this strips defaults as they're not yet consistent with protoc. We should fix the
   *     implementation to match protoc.
   */
  FileDescriptorProto stripOptionsAndDefaults(FileDescriptorProto fileDescriptorProto) {
    List<DescriptorProto> messageTypeList = new ArrayList<>();
    for (DescriptorProto messageType : fileDescriptorProto.getMessageTypeList()) {
      messageTypeList.add(stripOptionsAndDefaults(messageType));
    }
    List<FieldDescriptorProto> extensionList = new ArrayList<>();
    for (FieldDescriptorProto extension : fileDescriptorProto.getExtensionList()) {
      extensionList.add(stripOptionsAndDefaults(extension));
    }
    return fileDescriptorProto.toBuilder()
        .clearMessageType()
        .addAllMessageType(messageTypeList)
        .clearExtension()
        .addAllExtension(extensionList)
        .build();
  }

  DescriptorProto stripOptionsAndDefaults(DescriptorProto descriptorProto) {
    List<DescriptorProto> nestedTypeList = new ArrayList<>();
    for (DescriptorProto nestedType : descriptorProto.getNestedTypeList()) {
      nestedTypeList.add(stripOptionsAndDefaults(nestedType));
    }
    List<FieldDescriptorProto> fieldList = new ArrayList<>();
    for (FieldDescriptorProto field : descriptorProto.getFieldList()) {
      fieldList.add(stripOptionsAndDefaults(field));
    }
    return descriptorProto.toBuilder()
        .clearNestedType()
        .addAllNestedType(nestedTypeList)
        .clearField()
        .addAllField(fieldList)
        .clearExtensionRange()
        .build();
  }

  FieldDescriptorProto stripOptionsAndDefaults(FieldDescriptorProto fieldDescriptorProto) {
    FieldDescriptorProto.Builder builder = fieldDescriptorProto.toBuilder()
        .clearDefaultValue();
    if (clearJsonName) {
      builder.clearJsonName();
    }
    return builder.build();
  }
}
