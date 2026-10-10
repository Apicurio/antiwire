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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles a consumer class against the real Wire 7.1.0 {@code wire-schema-jvm} jar, then runs it
 * against the port: the situation of a user who built code against upstream and swaps the jars.
 * It calls accessors by their upstream names ({@code getPackageName()}, {@code getTypes()},
 * {@code getName()}, {@code getTag()}) and fails with {@code NoSuchMethodError} when the port
 * exposes plain names instead.
 *
 * <p>The four upstream jars (wire-schema, wire-runtime, okio, kotlin-stdlib) are listed with URL
 * and SHA-256 under {@code upstream_jars} in {@code config/parity-pins.json}; the test fetches
 * them from Maven Central into the module's {@code target/upstream-jars} on first use and fails
 * when a checksum differs. It is skipped, with a stated reason, only when the network is
 * unavailable and the jars were never fetched; the consumer covers the parser element model
 * and the schema accessors that Apicurio calls, and {@code AccessorNameParityTest} (offline)
 * covers every other getter by reflection.
 */
public class CompiledAgainstUpstreamTest {
  private static final String CONSUMER = ""
      + "import com.squareup.wire.schema.internal.parser.MessageElement;\n"
      + "import com.squareup.wire.schema.internal.parser.ProtoFileElement;\n"
      + "public class Consumer {\n"
      + "  public static String run(ProtoFileElement e) {\n"
      + "    MessageElement m = (MessageElement) e.getTypes().get(0);\n"
      + "    return e.getPackageName() + '|' + m.getName() + '|' + m.getFields().get(0).getName()\n"
      + "        + '|' + m.getFields().get(0).getTag() + '|' + e.getLocation().getPath();\n"
      + "  }\n"
      + "}\n";

  @Test
  public void consumerCompiledAgainstUpstreamRunsAgainstThePort(@TempDir Path dir) throws Exception {
    File[] upstream = upstreamJars();
    Assumptions.assumeTrue(upstream != null,
        "upstream 7.1.0 jars unavailable (offline and never fetched); see config/parity-pins.json");
    StringBuilder cp = new StringBuilder();
    for (File f : upstream) cp.append(f.getAbsolutePath()).append(File.pathSeparator);

    Path src = dir.resolve("Consumer.java");
    Files.writeString(src, CONSUMER);
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    Path out = Files.createDirectories(dir.resolve("out"));
    int rc = javac.run(null, null, null, "-classpath", cp.toString(), "-d", out.toString(), src.toString());
    assertEquals(0, rc, "the consumer must compile against the real upstream jars");

    // Run it with the port's classes only: the current classloader has no upstream Wire.
    try (URLClassLoader loader = new URLClassLoader(new URL[] {out.toUri().toURL()},
        getClass().getClassLoader())) {
      Class<?> consumer = loader.loadClass("Consumer");
      Class<?> element = Class.forName("com.squareup.wire.schema.internal.parser.ProtoFileElement");
      Object parsed = Class.forName("com.squareup.wire.schema.internal.parser.ProtoParser")
          .getMethod("parse", com.squareup.wire.schema.Location.class, String.class)
          .invoke(null, com.squareup.wire.schema.Location.get("x.proto"),
              "syntax = \"proto3\"; package p; message M { string a = 7; }");
      Object result = consumer.getMethod("run", element).invoke(null, parsed);
      assertEquals("p|M|a|7|x.proto", result);
    }
  }

  /**
   * The Companion form is a binary contract: a jar compiled against Wire reads the static field
   * {@code X.Companion} and calls {@code X$Companion.m(...)}. Confluent's kafka-schema-registry
   * client does exactly that for {@code ProtoParser.Companion.parse} (ProtobufSchema.toProtoFile),
   * so the already compiled class is run against the port with no recompilation, and its bytecode
   * is checked to really reference the Companion field and class.
   */
  private static final String COMPANION_CONSUMER = ""
      + "import com.squareup.wire.ProtoAdapter;\n"
      + "import com.squareup.wire.schema.Location;\n"
      + "import com.squareup.wire.schema.internal.parser.ProtoFileElement;\n"
      + "import com.squareup.wire.schema.internal.parser.ProtoParser;\n"
      + "import com.squareup.wire.schema.ProtoType;\n"
      + "import com.squareup.wire.schema.ProtoMember;\n"
      + "public class CompanionConsumer {\n"
      + "  public static String run() {\n"
      + "    ProtoFileElement e = ProtoParser.Companion.parse(Location.get(\"x.proto\"),\n"
      + "        \"syntax = \\\"proto3\\\"; message M { string a = 1; }\");\n"
      + "    Location l = Location.Companion.get(\"base\", \"p.proto\");\n"
      + "    ProtoType t = ProtoType.Companion.get(\"a.b.C\");\n"
      + "    ProtoMember m = ProtoMember.Companion.get(\"a.b.C#d\");\n"
      + "    ProtoAdapter<?> a = ProtoAdapter.Companion.get(\"com.squareup.wire.ProtoAdapter#STRING\");\n"
      + "    return e.getTypes().size() + \"|\" + l.getPath() + \"|\" + t + \"|\" + m + \"|\" + (a != null);\n"
      + "  }\n"
      + "}\n";

  @Test
  public void companionFormCompiledAgainstUpstreamRunsAgainstThePort(@TempDir Path dir)
      throws Exception {
    File[] upstream = upstreamJars();
    Assumptions.assumeTrue(upstream != null,
        "upstream 7.1.0 jars unavailable (offline and never fetched); see config/parity-pins.json");
    StringBuilder cp = new StringBuilder();
    for (File f : upstream) cp.append(f.getAbsolutePath()).append(File.pathSeparator);
    Path src = dir.resolve("CompanionConsumer.java");
    Files.writeString(src, COMPANION_CONSUMER);
    Path out = Files.createDirectories(dir.resolve("out"));
    int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath", cp.toString(),
        "-d", out.toString(), src.toString());
    assertEquals(0, rc, "the consumer must compile against the real upstream jars");

    // Bytecode check: the compiled class must read the static fields and call the nested classes.
    String bytecode = javapConstants(out.resolve("CompanionConsumer.class"));
    for (String needed : new String[] {
        "com/squareup/wire/schema/internal/parser/ProtoParser.Companion:Lcom/squareup/wire/schema/internal/parser/ProtoParser$Companion;",
        "com/squareup/wire/schema/internal/parser/ProtoParser$Companion.parse:(Lcom/squareup/wire/schema/Location;Ljava/lang/String;)Lcom/squareup/wire/schema/internal/parser/ProtoFileElement;",
        "com/squareup/wire/schema/Location$Companion.get:(Ljava/lang/String;Ljava/lang/String;)Lcom/squareup/wire/schema/Location;",
        "com/squareup/wire/ProtoAdapter$Companion.get:(Ljava/lang/String;)Lcom/squareup/wire/ProtoAdapter;"}) {
      org.junit.jupiter.api.Assertions.assertTrue(bytecode.contains(needed),
          "compiled consumer lacks the expected binary reference " + needed);
    }

    try (URLClassLoader loader = new URLClassLoader(new URL[] {out.toUri().toURL()},
        getClass().getClassLoader())) {
      Object result = loader.loadClass("CompanionConsumer").getMethod("run").invoke(null);
      assertEquals("1|p.proto|a.b.C|a.b.C#d|true", result);
    }
  }

  private static String javapConstants(Path classFile) throws Exception {
    Process p = new ProcessBuilder("javap", "-v", "-cp", classFile.getParent().toString(),
        classFile.getFileName().toString().replace(".class", "")).redirectErrorStream(true).start();
    String text = new String(p.getInputStream().readAllBytes());
    assertEquals(0, p.waitFor(), "javap must run");
    return text.replaceAll("\\s+", " ");
  }

  private static File[] upstreamJars() throws Exception {
    File pins = new File("../config/parity-pins.json");
    String json = Files.readString(pins.toPath());
    File dir = new File("target/upstream-jars");
    dir.mkdirs();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
        "\"([\\w.-]+\\.jar)\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\"")
        .matcher(json);
    java.util.List<File> out = new java.util.ArrayList<>();
    while (m.find()) {
      File f = new File(dir, m.group(1));
      if (!f.exists() || !sha256(f).equals(m.group(3))) {
        // Download to a temp file and move into place only after the checksum matches, so an
        // interrupted fetch can never leave a truncated jar in the cache.
        File part = new File(dir, m.group(1) + ".part");
        try (java.io.InputStream in = new URL(m.group(2)).openStream()) {
          Files.copy(in, part.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
          return null;
        }
        assertEquals(m.group(3), sha256(part), "checksum of " + m.group(1));
        Files.move(part.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      out.add(f);
    }
    assertEquals(4, out.size(), "upstream_jars entries in config/parity-pins.json");
    return out.toArray(new File[0]);
  }

  private static String sha256(File f) throws Exception {
    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
    StringBuilder hex = new StringBuilder();
    for (byte b : md.digest(Files.readAllBytes(f.toPath()))) hex.append(String.format("%02x", b));
    return hex.toString();
  }
}
