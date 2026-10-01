/*
 * Copyright (C) 2026 the antiwire authors
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
import java.util.Objects;

/**
 * The Person and Task fixtures upstream shares between ReverseProtoWriterTest and
 * ProtoReaderTest, as Java records of the same shape (name + birthYear, id + task). Their
 * adapters use hand-written forEachTag-style loops, here in explicit begin/nextTag form.
 */
final class TestMessages {
  private TestMessages() {
  }

  static final class Person {
    static final ProtoAdapter<Person> ADAPTER = new ProtoAdapter<Person>(
        FieldEncoding.LENGTH_DELIMITED, Person.class, "type.googleapis.com/Person",
        Syntax.PROTO_2, null, null) {
      @Override public Person redact(Person value) {
        throw new IllegalStateException("unexpected call");
      }

      @Override public int encodedSize(Person value) {
        return STRING.encodedSizeWithTag(1, value.name)
            + INT32.encodedSizeWithTag(2, value.birthYear);
      }

      @Override public void encode(ProtoWriter writer, Person value) throws IOException {
        STRING.encodeWithTag(writer, 1, value.name);
        INT32.encodeWithTag(writer, 2, value.birthYear);
      }

      @Override public void encode(ReverseProtoWriter writer, Person value) throws IOException {
        INT32.encodeWithTag(writer, 2, value.birthYear);
        STRING.encodeWithTag(writer, 1, value.name);
      }

      @Override public Person decode(ProtoReader reader) throws IOException {
        String name = "";
        int birthYear = 0;
        long token = reader.beginMessage();
        int tag;
        while ((tag = reader.nextTag()) != -1) {
          switch (tag) {
            case 1:
              name = STRING.decode(reader);
              break;
            case 2:
              birthYear = INT32.decode(reader);
              break;
            default:
              reader.readUnknownField(tag);
          }
        }
        reader.endMessageAndGetUnknownFields(token);
        return new Person(name, birthYear);
      }
    };

    final String name;
    final int birthYear;

    Person(String name, int birthYear) {
      this.name = name;
      this.birthYear = birthYear;
    }

    @Override public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof Person)) return false;
      Person that = (Person) other;
      return name.equals(that.name) && birthYear == that.birthYear;
    }

    @Override public int hashCode() {
      return 31 * name.hashCode() + birthYear;
    }

    @Override public String toString() {
      return "Person(name=" + name + ", birthYear=" + birthYear + ")";
    }
  }

  /** Supports reverse encoding, but has a field that only supports forward encoding. */
  static final class Task {
    static final ProtoAdapter<Task> ADAPTER = new ProtoAdapter<Task>(
        FieldEncoding.LENGTH_DELIMITED, Task.class, "type.googleapis.com/Task",
        Syntax.PROTO_2, null, null) {
      @Override public Task redact(Task value) {
        throw new IllegalStateException("unexpected call");
      }

      @Override public int encodedSize(Task value) {
        return STRING.encodedSizeWithTag(1, value.description)
            + Person.ADAPTER.encodedSizeWithTag(2, value.assignee);
      }

      @Override public void encode(ProtoWriter writer, Task value) throws IOException {
        STRING.encodeWithTag(writer, 1, value.description);
        Person.ADAPTER.encodeWithTag(writer, 2, value.assignee);
      }

      @Override public void encode(ReverseProtoWriter writer, Task value) throws IOException {
        Person.ADAPTER.encodeWithTag(writer, 2, value.assignee);
        STRING.encodeWithTag(writer, 1, value.description);
      }

      @Override public Task decode(ProtoReader reader) throws IOException {
        String description = "";
        Person assignee = null;
        long token = reader.beginMessage();
        int tag;
        while ((tag = reader.nextTag()) != -1) {
          switch (tag) {
            case 1:
              description = STRING.decode(reader);
              break;
            case 2:
              assignee = Person.ADAPTER.decode(reader);
              break;
            default:
              reader.readUnknownField(tag);
          }
        }
        reader.endMessageAndGetUnknownFields(token);
        return new Task(description, assignee);
      }
    };

    final String description;
    final Person assignee;

    Task(String description, Person assignee) {
      this.description = description;
      this.assignee = assignee;
    }

    @Override public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof Task)) return false;
      Task that = (Task) other;
      return description.equals(that.description)
          && java.util.Objects.equals(assignee, that.assignee);
    }

    @Override public int hashCode() {
      return 31 * description.hashCode() + java.util.Objects.hashCode(assignee);
    }

    @Override public String toString() {
      return "Task(description=" + description + ", assignee=" + assignee + ")";
    }
  }
}
