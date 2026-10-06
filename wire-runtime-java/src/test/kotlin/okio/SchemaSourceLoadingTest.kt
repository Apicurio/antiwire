/*
 * M0 spike demonstration (TASK-4 AC#2): schema-source access from in-memory, filesystem,
 * classpath-resource, and ZIP origins through the selected I/O route, mirroring the exact
 * call shapes of the wire-schema loader at wire tag 7.1.0:
 *  - Roots.kt: openZip, metadataOrNull, listRecursively, "/".toPath(), read { ... }
 *  - CoreLoader.kt: asResourceFileSystem, "/".toPath() / path, read { readUtf8() }
 *  - fileSystems.kt: readBomAsCharset via select(Options) over the UNICODE_BOMS table
 *  - the Apicurio census pattern: FakeFileSystem + openReadWrite + FileHandle.write
 * Not an adapted upstream test: it is new spike code pinning the four loading origins.
 */
package okio

import java.nio.charset.Charset
import java.nio.file.Files
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SchemaSourceLoadingTest {

  @TempDir
  lateinit var tempDir: java.nio.file.Path

  /** Mirrors wire-schema jvmMain internal/fileSystems.kt. */
  private val unicodeBoms = Options.of(
    ByteString.decodeHex("efbbbf"), // UTF-8
    ByteString.decodeHex("feff"), // UTF-16BE
    ByteString.decodeHex("fffe0000"), // UTF-32LE
    ByteString.decodeHex("fffe"), // UTF-16LE
    ByteString.decodeHex("0000feff"), // UTF-32BE
  )

  /** Mirrors wire-schema jvmMain internal/fileSystems.kt. */
  private fun BufferedSource.readBomAsCharset(default: Charset = Charsets.UTF_8): Charset =
    when (select(unicodeBoms)) {
      0 -> Charsets.UTF_8
      1 -> Charsets.UTF_16BE
      2 -> Charsets.UTF_32LE
      3 -> Charsets.UTF_16LE
      4 -> Charsets.UTF_32BE
      -1 -> default
      else -> throw AssertionError()
    }

  /** Mirrors the Roots.kt ProtoFilePath.parse call: fs.read(path) { readBomAsCharset + readString }. */
  private fun loadViaReader(fileSystem: FileSystem, path: Path): String {
    return fileSystem.read(path) { source ->
      val charset = source.readBomAsCharset()
      source.readString(charset)
    }
  }

  @Test fun inMemoryFileSystemLoadsSchemaSource() {
    // The Apicurio ProtobufSchemaLoader pattern.
    val inMemoryFileSystem = FakeFileSystem()
    inMemoryFileSystem.setWorkingDirectory(Path.get("/"))
    val path = Path.get("/google/protobuf/descriptor.proto")
    inMemoryFileSystem.createDirectories(path.getParent()!!)
    val content = "syntax = \"proto2\";\nmessage InMemory {}\n".toByteArray(Charsets.UTF_8)
    val fileHandle = inMemoryFileSystem.openReadWrite(path)
    fileHandle.write(0, content, 0, content.size)
    fileHandle.close()

    assertTrue(inMemoryFileSystem.exists(path))
    assertEquals(
      "syntax = \"proto2\";\nmessage InMemory {}\n",
      loadViaReader(inMemoryFileSystem, path),
    )
  }

  @Test fun filesystemLoadsSchemaSource() {
    val root = Path.get(tempDir.toString())
    val dir = root.resolve("squareup/dinosaurs")
    FileSystem.SYSTEM.createDirectories(dir)
    val path = dir.resolve("dino.proto")
    FileSystem.SYSTEM.write(path) { sink -> sink.writeUtf8("syntax = \"proto2\";\n") }

    assertTrue(FileSystem.SYSTEM.exists(path))
    assertNotNull(FileSystem.SYSTEM.metadataOrNull(path)!!.isRegularFile)
    assertTrue(FileSystem.SYSTEM.metadataOrNull(dir)!!.isDirectory!!)

    val listed = FileSystem.SYSTEM.listRecursively(root).filter { it.getName().endsWith(".proto") }
        .toList()
    assertEquals(listOf(path), listed)
    assertEquals("syntax = \"proto2\";\n", loadViaReader(FileSystem.SYSTEM, path))
  }

  @Test fun filesystemReaderHonorsBom() {
    val root = Path.get(tempDir.toString())
    val path = root.resolve("bom.proto")
    // UTF-8 BOM followed by UTF-8 text: readBomAsCharset must consume the BOM and decode as UTF-8.
    FileSystem.SYSTEM.write(path) { sink ->
      sink.write(ByteString.decodeHex("efbbbf"))
      sink.writeUtf8("message Bom {}")
    }
    assertEquals("message Bom {}", loadViaReader(FileSystem.SYSTEM, path))
  }

  @Test fun classpathResourcesLoadSchemaSource() {
    // The CoreLoader pattern: the classpath loader reads well-known protos by resource path.
    val resourceFileSystem = FileSystem.asResourceFileSystem(
      SchemaSourceLoadingTest::class.java.classLoader,
    )
    val path = Path.get("/").resolve("wire/descriptor.proto")
    val text = loadViaReader(resourceFileSystem, path)
    assertTrue(text.startsWith("syntax = \"proto2\";"))
    assertTrue("FileDescriptorProto" in text)
  }

  @Test fun zipLoadsSchemaSource() {
    // Build a zip holding proto files, exactly what Roots.kt hands to openZip.
    val zipPath = Path.get(Files.createTempFile(tempDir, "schemas", ".zip").toString())
    java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipPath.toFile())).use { zip ->
      zip.putNextEntry(java.util.zip.ZipEntry("squareup/dinosaurs/dino.proto"))
      zip.write("message Dino {}".toByteArray(Charsets.UTF_8))
      zip.closeEntry()
      zip.putNextEntry(java.util.zip.ZipEntry("squareup/dinosaurs/nested/deep.proto"))
      zip.write("message Deep {}".toByteArray(Charsets.UTF_8))
      zip.closeEntry()
      // A deflated entry exercises InflaterSource inside the zip file system.
      zip.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
      zip.putNextEntry(java.util.zip.ZipEntry("squareup/compressed.proto"))
      zip.write("message Compressed {}".toByteArray(Charsets.UTF_8))
      zip.closeEntry()
    }

    val zipFileSystem = FileSystem.openZip(zipPath)
    val protos = zipFileSystem.listRecursively(Path.get("/"))
        .filter { it.getName().endsWith(".proto") }
        .toList()
    assertEquals(
      listOf(
        Path.get("/squareup/compressed.proto"),
        Path.get("/squareup/dinosaurs/dino.proto"),
        Path.get("/squareup/dinosaurs/nested/deep.proto"),
      ),
      protos,
    )
    assertEquals("message Dino {}", loadViaReader(zipFileSystem, Path.get("/squareup/dinosaurs/dino.proto")))
    assertEquals(
      "message Compressed {}",
      loadViaReader(zipFileSystem, Path.get("/squareup/compressed.proto")),
    )
    assertFalse(zipFileSystem.exists(Path.get("/squareup/missing.proto")))
  }

  @Test fun pathOperationsMirrorLoaderUsage() {
    // Roots.kt/Util.kt path algebra: resolve with imports, relativeTo against a root.
    val root = Path.get("/squareup/dinosaurs")
    val resolved = root.resolve("dino.proto")
    assertEquals("/squareup/dinosaurs/dino.proto", resolved.toString())
    assertEquals("dino.proto", resolved.getName())
    assertEquals(root, resolved.getParent())
    assertEquals("dino.proto", resolved.relativeTo(root).toString())
    assertEquals("a/b.proto", Path.get("a\\b.proto").toString().replace('\\', '/'))
  }
}
