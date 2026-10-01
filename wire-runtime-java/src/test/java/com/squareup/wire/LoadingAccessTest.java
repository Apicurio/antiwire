package com.squareup.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okio.Buffer;
import okio.BufferedSink;
import okio.FileSystem;
import okio.FileMetadata;
import okio.Okio;
import okio.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-4 AC#2: representative schema-source access on every route the port keeps open,
 * in-memory, host filesystem, classpath resources and ZIP archives, with no Kotlin and no
 * third-party production dependency. Path semantics come from the platform nio file system.
 */
public class LoadingAccessTest {
  @TempDir java.nio.file.Path tempDir;

  private static String readUtf8(FileSystem fs, Path path) throws IOException {
    try (okio.BufferedSource source = Okio.buffer(fs.source(path))) {
      return source.readUtf8();
    }
  }

  private static void writeString(FileSystem fs, Path path, String content) throws IOException {
    try (BufferedSink sink = Okio.buffer(fs.sink(path, false))) {
      sink.writeUtf8(content);
    }
  }

  /**
   * Path.relativeTo follows okio 3's lexical contract (verified against okio-jvm 3.18.2 via the
   * parity jar): the argument is the base, equal paths yield ".", siblings need ".." hops, and
   * foreign-provider paths relativize without any nio provider participating.
   */
  @Test public void relativeToMatchesOkioSemantics() {
    assertEquals("c.txt", Path.get("/a/b/c.txt").relativeTo(Path.get("/a/b")).toString());
    assertEquals(".", Path.get("/a").relativeTo(Path.get("/a")).toString());
    assertEquals(".", Path.get("/a/b").relativeTo(Path.get("/a/b/")).toString());
    assertEquals("../b", Path.get("/a/b").relativeTo(Path.get("/a/x")).toString());
    assertEquals("../a/b", Path.get("/a/b").relativeTo(Path.get("/x")).toString());
    assertEquals("b", Path.get("a/b").relativeTo(Path.get("a")).toString());
    assertEquals("a/b", Path.get("a/b").relativeTo(Path.get(".")).toString());
    // '..' in the base past the common prefix is impossible, like okio.
    assertThrows(IllegalArgumentException.class,
        () -> Path.get("/a/b").relativeTo(Path.get("/a/../c")));
    // '..' in this path is kept.
    assertEquals("../b", Path.get("/a/../b").relativeTo(Path.get("/a")).toString());
  }

  /** A zipfs entry relativizes against a host-path base although their providers differ. */
  @Test public void relativeToIsProviderAgnostic() throws IOException {
    java.nio.file.Path zip = tempDir.resolve("z.zip");
    try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(
        java.nio.file.Files.newOutputStream(zip))) {
      out.putNextEntry(new java.util.zip.ZipEntry("dir/entry.proto"));
      out.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.closeEntry();
    }
    try (FileSystem zipFs = FileSystem.SYSTEM.openZip(Path.get(zip.toString()))) {
      Path entry = zipFs.list(Path.get("/dir")).get(0);
      Path base = Path.get("/dir");
      assertEquals("entry.proto", entry.relativeTo(base).toString());
    }
  }

  /** In-memory: buffers remain the primary in-memory source and sink. */
  @Test public void inMemoryAccess() throws IOException {
    Buffer buffer = new Buffer();
    new ProtoWriter(buffer).writeVarint32(150);
    assertEquals("9601", buffer.readByteString().hex());
  }

  /** Host filesystem: write, read back, metadata, list, atomic move, delete. */
  @Test public void filesystemAccess() throws IOException {
    FileSystem fs = FileSystem.SYSTEM;
    Path dir = Path.get(tempDir.toString()).div("proto");
    fs.createDirectories(dir, false);
    Path file = dir.div("a.proto");
    writeString(fs, file, "syntax = \"proto3\";\n");

    assertEquals("syntax = \"proto3\";\n", readUtf8(fs, file));
    assertTrue(fs.metadataOrNull(file).isRegularFile);
    assertTrue(fs.metadataOrNull(dir).isDirectory);
    assertEquals(1, fs.list(dir).size());

    Path moved = dir.div("b.proto");
    fs.atomicMove(file, moved);
    assertNull(fs.metadataOrNull(file));
    assertTrue(fs.exists(moved));
    fs.delete(moved, true);
  }

  /** Directory creation: single-level strict create versus parent-creating chain. */
  @Test public void directoryCreationSemantics() throws IOException {
    FileSystem fs = FileSystem.SYSTEM;
    Path base = Path.get(tempDir.toString());
    // No parents: createDirectory fails however mustCreate is set.
    assertThrows(IOException.class, () -> fs.createDirectory(base.div("a").div("b"), false));
    // The chain creates the parents, idempotently, and mustCreate still refuses existing.
    fs.createDirectories(base.div("a").div("b"), false);
    fs.createDirectories(base.div("a").div("b"), false);
    assertThrows(IOException.class,
        () -> fs.createDirectories(base.div("a").div("b"), true));
  }

  /** The absent-directory contract: listOrNull returns null, list throws FileNotFound. */
  @Test public void absentDirectoryContract() throws IOException {
    FileSystem fs = FileSystem.SYSTEM;
    Path missing = Path.get(tempDir.toString()).div("does-not-exist");
    assertNull(fs.listOrNull(missing));
    assertThrows(FileNotFoundException.class, () -> fs.list(missing));
  }

  /** listRecursively walks depth-first, parents before children, directories included. */
  @Test public void recursiveListing() throws IOException {
    FileSystem fs = FileSystem.SYSTEM;
    Path base = Path.get(tempDir.toString()).div("tree");
    fs.createDirectories(base.div("b"), false);
    writeString(fs, base.div("root.proto"), "r");
    writeString(fs, base.div("b").div("nested.proto"), "n");
    assertEquals(
        java.util.Arrays.asList(
            base.div("b"), base.div("b").div("nested.proto"), base.div("root.proto")),
        fs.listRecursively(base));
  }

  /** Symlinks report as themselves: symlinkTarget populated, not the target's attributes. */
  @Test public void symlinkMetadata() throws IOException {
    FileSystem fs = FileSystem.SYSTEM;
    Path base = Path.get(tempDir.toString());
    Path target = base.div("target.proto");
    writeString(fs, target, "x");
    Path link = base.div("link.proto");
    Files.createSymbolicLink(
        java.nio.file.Paths.get(link.toString()), java.nio.file.Paths.get(target.toString()));

    FileMetadata metadata = fs.metadataOrNull(link);
    assertNotNull(metadata.symlinkTarget);
    assertEquals(target.normalized(), metadata.symlinkTarget.normalized());
    assertFalse(metadata.isRegularFile);
    assertTrue(fs.metadataOrNull(target).isRegularFile);
  }

  /** Classpath: known resource paths read through the resource file system. */
  @Test public void classpathAccess() throws IOException {
    FileSystem fs = FileSystem.asResourceFileSystem(getClass().getClassLoader());
    Path resource = Path.get("/loading/hello.proto");
    FileMetadata metadata = fs.metadataOrNull(resource);
    assertNotNull(metadata);
    assertTrue(metadata.isRegularFile);
    assertEquals("syntax = \"proto3\";\nmessage Hello { string greeting = 1; }\n",
        readUtf8(fs, resource));
    assertThrows(FileNotFoundException.class,
        () -> readUtf8(fs, Path.get("/loading/absent.proto")));
  }

  /** ZIP: open a real archive, read entries, compose paths, and close the handle. */
  @Test public void zipAccess() throws IOException {
    java.nio.file.Path zip = tempDir.resolve("protos.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("squareup/hello.proto"));
      out.write("syntax = \"proto3\";\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.closeEntry();
    }

    try (FileSystem zipfs = FileSystem.SYSTEM.openZip(Path.get(zip.toString()))) {
      // Composition happens inside the ZIP provider; the entries root is /.
      Path dir = Path.get("/squareup");
      Path entry = dir.div("hello.proto");
      assertEquals("syntax = \"proto3\";\n", readUtf8(zipfs, entry));
      FileMetadata metadata = zipfs.metadataOrNull(entry);
      assertNotNull(metadata);
      assertTrue(metadata.isRegularFile);
      assertEquals(1, zipfs.list(dir).size());
      // An absolute child still replaces the left-hand side entirely.
      assertEquals(Path.get("/squareup/hello.proto"), dir.div(entry));
    }
    // After close, the archive handle is released; the provider rejects further use. The JDK
    // zipfs signals a closed file system either as ClosedFileSystemException or as an NPE
    // from its internals depending on the entry point, so the assertion targets the runtime
    // failure itself rather than one specific JDK exception class.
    FileSystem closed = FileSystem.SYSTEM.openZip(Path.get(zip.toString()));
    closed.close();
    assertThrows(RuntimeException.class, () -> closed.listOrNull(Path.get("/squareup")));
  }

  /** Path semantics the schema loader relies on: absolute div replaces, normalize resolves. */
  @Test public void pathSemantics() {
    Path base = Path.get(tempDir.toString());
    // Normalization resolves ".." and "." segments.
    assertEquals(base, base.div("x").div("..").normalized());
    assertEquals(base, base.div("x").normalized().parent());
    // An absolute child replaces the left-hand side entirely (okio div semantics).
    Path absolute = Path.get("/definitely/absolute.proto");
    assertEquals(absolute, base.div(absolute));
    assertNull(Path.get("relative.proto").root());
    assertEquals(Path.get("/"), Path.get("/a/b").root());
  }
}
