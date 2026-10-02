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
package com.squareup.wire.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.squareup.wire.schema.internal.parser.ExtendElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.OptionElement.Kind;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.RpcElement;
import com.squareup.wire.schema.internal.parser.ServiceElement;
import com.squareup.wire.schema.internal.parser.TypeElement;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk to JUnit 5). Upstream calls {@code ProtoFile.get} with
 * the element only; no loader is involved on either side.
 */
public class ProtoFileTest {
  private final Location location = Location.get("file.proto");

  @Test public void roundTripToElement() {
    // TASK-13 adaptation (R1): named-argument element constructors rewritten to positional form,
    // filling omitted arguments with the upstream defaults from the pinned declarations.
    MessageElement element1 = new MessageElement(location.at(11, 1), "Message1",
        "Some comments about Message1",
        Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    MessageElement element2 = new MessageElement(location.at(12, 1), "Message2", "",
        Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(),
        Collections.singletonList(new FieldElement(location.at(13, 3), null, "string", "field",
            null, null, 1, "", Collections.emptyList())),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());

    ExtendElement extend1 = new ExtendElement(location.at(16, 1), "Extend1", "",
        Collections.emptyList());
    ExtendElement extend2 = new ExtendElement(location.at(17, 1), "Extend2", "",
        Collections.emptyList());
    OptionElement option1 = OptionElement.create("kit", Kind.STRING, "kat");
    OptionElement option2 = OptionElement.create("foo", Kind.STRING, "bar");
    RpcElement rpc = new RpcElement(location.at(20, 3), "MethodA", "", "Message2", "Message1",
        false, false,
        Collections.singletonList(OptionElement.create("methodoption", Kind.NUMBER, 1)));
    ServiceElement service1 = new ServiceElement(location.at(19, 1), "Service1", "",
        Collections.singletonList(rpc), Collections.emptyList());
    ServiceElement service2 = new ServiceElement(location.at(24, 1), "Service2", "",
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement fileElement = new ProtoFileElement(location, "example.simple", null,
        Collections.singletonList("example.thing"),
        Collections.singletonList("example.other"), Collections.emptyList(),
        Arrays.asList((TypeElement) element1, element2),
        Arrays.asList(service1, service2),
        Arrays.asList(extend1, extend2),
        Arrays.asList(option1, option2));
    ProtoFile file = ProtoFile.get(fileElement);

    // TASK-13 adaptation: Kotlin trimMargin raw string rendered as explicit concatenation.
    String expected = "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "package example.simple;\n"
        + "\n"
        + "import \"example.thing\";\n"
        + "import public \"example.other\";\n"
        + "\n"
        + "option kit = \"kat\";\n"
        + "option foo = \"bar\";\n"
        + "\n"
        + "// Some comments about Message1\n"
        + "message Message1 {}\n"
        + "\n"
        + "message Message2 {\n"
        + "  string field = 1;\n"
        + "}\n"
        + "\n"
        + "extend Extend1 {}\n"
        + "\n"
        + "extend Extend2 {}\n"
        + "\n"
        + "service Service1 {\n"
        + "  rpc MethodA (Message2) returns (Message1) {\n"
        + "    option methodoption = 1;\n"
        + "  };\n"
        + "}\n"
        + "\n"
        + "service Service2 {}\n";

    assertEquals(expected, file.toSchema());
    assertEquals(fileElement, file.toElement());
  }
}
