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
package com.squareup.wire;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.StreamCorruptedException;

/**
 * Replaces a {@link Message} during Java serialization with its encoded bytes and class, so the
 * message does not need to be {@link Serializable} field-by-field. Upstream declares this
 * Kotlin-internal; it is package-private here, and its readResolve looks up the ADAPTER field
 * directly: the general reflection registry (ProtoAdapter.get) is owned by TASK-7, and this is
 * the minimal form the ownership map assigns to this batch.
 */
class MessageSerializedForm<M extends Message<M, B>, B extends Message.Builder<M, B>>
    implements Serializable {
  private static final long serialVersionUID = 0L;

  private final byte[] bytes;
  private final Class<M> messageClass;

  MessageSerializedForm(byte[] bytes, Class<M> messageClass) {
    this.bytes = bytes;
    this.messageClass = messageClass;
  }

  Object readResolve() throws ObjectStreamException {
    ProtoAdapter<M> adapter;
    try {
      // Extensions will be decoded as unknown values.
      adapter = (ProtoAdapter<M>) messageClass.getField("ADAPTER").get(null);
    } catch (IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalArgumentException(
          "failed to access " + messageClass.getName() + "#ADAPTER", e);
    }
    try {
      return adapter.decode(bytes);
    } catch (IOException e) {
      throw new StreamCorruptedException(e.getMessage());
    }
  }
}
