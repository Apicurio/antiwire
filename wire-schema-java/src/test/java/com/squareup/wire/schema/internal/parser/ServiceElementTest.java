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
package com.squareup.wire.schema.internal.parser;

import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.squareup.wire.schema.Location;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Upstream ServiceElementTest translated (assertk to JUnit 5). */
public class ServiceElementTest {
  private final Location location = Location.get("file.proto");

  @Test public void emptyToSchema() {
    ServiceElement service = new ServiceElement(location, "Service", "",
        Collections.emptyList(), Collections.emptyList());
    assertEquals("service Service {}\n", service.toSchema());
  }

  @Test public void singleToSchema() {
    ServiceElement service = new ServiceElement(location, "Service", "",
        Collections.singletonList(rpc("Name")),
        Collections.emptyList());
    assertEquals("service Service {\n"
        + "  rpc Name (RequestType) returns (ResponseType);\n"
        + "}\n", service.toSchema());
  }

  @Test public void addMultipleRpcs() {
    RpcElement firstName = rpc("FirstName");
    RpcElement lastName = rpc("LastName");
    ServiceElement service = new ServiceElement(location, "Service", "",
        Arrays.asList(firstName, lastName), Collections.emptyList());
    assertEquals(2, service.getRpcs().size());
  }

  @Test public void singleWithOptionsToSchema() {
    ServiceElement service = new ServiceElement(location, "Service", "",
        Collections.singletonList(rpc("Name")),
        Collections.singletonList(OptionElement.create("foo", STRING, "bar")));
    assertEquals("service Service {\n"
        + "  option foo = \"bar\";\n"
        + "\n"
        + "  rpc Name (RequestType) returns (ResponseType);\n"
        + "}\n", service.toSchema());
  }

  @Test public void addMultipleOptions() {
    OptionElement kitKat = OptionElement.create("kit", STRING, "kat");
    OptionElement fooBar = OptionElement.create("foo", STRING, "bar");
    ServiceElement service = new ServiceElement(location, "Service", "",
        Collections.singletonList(rpc("Name")),
        Arrays.asList(kitKat, fooBar));
    assertEquals(2, service.getOptions().size());
  }

  @Test public void singleWithDocumentationToSchema() {
    ServiceElement service = new ServiceElement(location, "Service", "Hello",
        Collections.singletonList(rpc("Name")),
        Collections.emptyList());
    assertEquals("// Hello\n"
        + "service Service {\n"
        + "  rpc Name (RequestType) returns (ResponseType);\n"
        + "}\n", service.toSchema());
  }

  @Test public void multipleToSchema() {
    RpcElement rpc = rpc("Name");
    ServiceElement service = new ServiceElement(location, "Service", "",
        Arrays.asList(rpc, rpc), Collections.emptyList());
    assertEquals("service Service {\n"
        + "  rpc Name (RequestType) returns (ResponseType);\n"
        + "  rpc Name (RequestType) returns (ResponseType);\n"
        + "}\n", service.toSchema());
  }

  @Test public void rpcToSchema() {
    RpcElement rpc = rpc("Name");
    assertEquals("rpc Name (RequestType) returns (ResponseType);\n", rpc.toSchema());
  }

  @Test public void rpcWithDocumentationToSchema() {
    RpcElement rpc = new RpcElement(location, "Name", "Hello", "RequestType", "ResponseType",
        false, false, Collections.emptyList());
    assertEquals("// Hello\n"
        + "rpc Name (RequestType) returns (ResponseType);\n", rpc.toSchema());
  }

  @Test public void rpcWithOptionsToSchema() {
    RpcElement rpc = new RpcElement(location, "Name", "", "RequestType", "ResponseType",
        false, false, Collections.singletonList(OptionElement.create("foo", STRING, "bar")));
    assertEquals("rpc Name (RequestType) returns (ResponseType) {\n"
        + "  option foo = \"bar\";\n"
        + "};\n", rpc.toSchema());
  }

  @Test public void rpcWithRequestStreamingToSchema() {
    RpcElement rpc = new RpcElement(location, "Name", "", "RequestType", "ResponseType",
        true, false, Collections.emptyList());
    assertEquals("rpc Name (stream RequestType) returns (ResponseType);\n", rpc.toSchema());
  }

  @Test public void rpcWithResponseStreamingToSchema() {
    RpcElement rpc = new RpcElement(location, "Name", "", "RequestType", "ResponseType",
        false, true, Collections.emptyList());
    assertEquals("rpc Name (RequestType) returns (stream ResponseType);\n", rpc.toSchema());
  }

  private RpcElement rpc(String name) {
    return new RpcElement(location, name, "", "RequestType", "ResponseType", false, false,
        Collections.emptyList());
  }
}
