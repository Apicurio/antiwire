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
package com.squareup.wire.schema.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.OptionElement;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * TASK-16 adoption of upstream ProfileParserTest:
 * {@code wire-schema/src/commonTest/kotlin/com/squareup/wire/schema/internal/ProfileParserTest.kt}
 * at square/wire tag 7.1.0. Inputs and expected elements, locations, and error messages are
 * verbatim; assertk's {@code hasMessage} becomes an exact-message assertion and Kotlin's
 * named-argument element construction becomes positional with upstream's declared defaults
 * (rules R1/R2 of docs/translation-conventions.md section 6.3).
 */
public class ProfileParserTest {
  private Location location = Location.get("android.wire");

  @Test
  public void parseType() {
    String proto = ""
        + "syntax = \"wire2\";\n"
        + "package squareup.dinosaurs;\n"
        + "\n"
        + "import \"squareup/geology/period.proto\";\n"
        + "\n"
        + "/* Roar! */\n"
        + "type squareup.dinosaurs.Dinosaur {\n"
        + "  target com.squareup.dino.Dinosaur using com.squareup.dino.Dinosaurs#DINO_ADAPTER;\n"
        + "}\n"
        + "\n"
        + "type squareup.geology.Period {\n"
        + "  with custom_type = \"duration\";\n"
        + "  target java.time.Period using com.squareup.time.Time#PERIOD_ADAPTER;\n"
        + "}\n";
    ProfileFileElement expected = new ProfileFileElement(
        location,
        "squareup.dinosaurs",
        Collections.singletonList("squareup/geology/period.proto"),
        Arrays.asList(
            new TypeConfigElement(
                location.at(7, 1),
                "squareup.dinosaurs.Dinosaur",
                "Roar!",
                Collections.emptyList(),
                "com.squareup.dino.Dinosaur",
                "com.squareup.dino.Dinosaurs#DINO_ADAPTER"),

            new TypeConfigElement(
                location.at(11, 1),
                "squareup.geology.Period",
                "",
                Collections.singletonList(
                    OptionElement.create(
                        "custom_type",
                        OptionElement.Kind.STRING,
                        "duration")),
                "java.time.Period",
                "com.squareup.time.Time#PERIOD_ADAPTER")));

    ProfileParser parser = new ProfileParser(location, proto);
    assertEquals(expected, parser.read());
  }

  @Test
  public void invalidSyntax() {
    String proto = ""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "type squareup.dinosaurs.Dinosaur {\n"
        + "  target com.squareup.dino.Dinosaur using com.squareup.dino.Dinosaurs#DINO_ADAPTER;\n"
        + "}\n";
    ProfileParser parser = new ProfileParser(location, proto);
    IllegalStateException e = assertThrows(IllegalStateException.class, parser::read);
    assertEquals("Syntax error in android.wire:1:18: expected 'wire2'", e.getMessage());
  }

  @Test
  public void tooManyTargets() {
    String proto = ""
        + "syntax = \"wire2\";\n"
        + "\n"
        + "type squareup.dinosaurs.Dinosaur {\n"
        + "  target com.squareup.dino.Herbivore using com.squareup.dino.Dinosaurs#PLANT_ADAPTER;\n"
        + "  target com.squareup.dino.Carnivore using com.squareup.dino.Dinosaurs#MEAT_ADAPTER;\n"
        + "}\n";
    ProfileParser parser = new ProfileParser(location, proto);
    IllegalStateException e = assertThrows(IllegalStateException.class, parser::read);
    assertEquals("Syntax error in android.wire:5:3: too many targets", e.getMessage());
  }

  @Test
  public void readAndWriteRoundTrip() {
    String proto = ""
        + "// android.wire\n"
        + "syntax = \"wire2\";\n"
        + "package squareup.dinosaurs;\n"
        + "\n"
        + "import \"squareup/geology/period.proto\";\n"
        + "\n"
        + "// Roar!\n"
        + "type squareup.dinosaurs.Dinosaur {\n"
        + "  target com.squareup.dino.Dinosaur using com.squareup.dino.Dinosaurs#DINO_ADAPTER;\n"
        + "}\n"
        + "type squareup.geology.Period {\n"
        + "  with custom_type = \"duration\";\n"
        + "  target java.time.Period using com.squareup.time.Time#PERIOD_ADAPTER;\n"
        + "}\n";
    ProfileParser parser = new ProfileParser(location, proto);
    assertEquals(proto, parser.read().toSchema());
  }
}
