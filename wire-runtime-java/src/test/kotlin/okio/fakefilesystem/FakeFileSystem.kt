/*
 * Minimal in-memory FileSystem for the port's test scope (TASK-4 M0 spike).
 *
 * Upstream wire tests and Apicurio's ProtobufSchemaLoader both drive the loader through
 * okio.fakefilesystem.FakeFileSystem (upstream artifact okio-fakefilesystem, Kotlin).
 * Whether the port vendors a Java equivalent in production or the Apicurio call site is
 * patched is the TASK-12/TASK-18 loading-boundary decision (docs/decisions.md later-mile
 * owners); this test-scope stand-in exists so the M0 spike can demonstrate the in-memory
 * schema-source path through the same call shapes: setWorkingDirectory, createDirectories,
 * openReadWrite + FileHandle.write + close, exists, and read.
 *
 * Not a translation of upstream okio-fakefilesystem (which carries openZip delegation,
 * symlink emulation, and an openFiles audit log); every unsupported operation throws
 * IOException instead of being emulated.
 */
package okio.fakefilesystem

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import okio.Buffer
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.Okio
import okio.Path
import okio.Sink
import okio.Source
import okio.Timeout

class FakeFileSystem : FileSystem() {

  private val files = mutableMapOf<Path, ByteArray>()
  private val directories = mutableSetOf<Path>()
  private var workingDirectory: Path = Path.get("/")

  fun setWorkingDirectory(path: Path) {
    workingDirectory = path
  }

  override fun canonicalize(path: Path): Path = path

  override fun metadataOrNull(path: Path): FileMetadata {
    files[path]?.let { bytes ->
      return FileMetadata(true, false, null, bytes.size.toLong(), null, 0L, null)
    }
    if (path in directories || path == workingDirectory) {
      return FileMetadata(false, true, null, null, null, 0L, null)
    }
    return FileMetadata(false, false, null, null, null, null, null)
  }

  override fun listOrNull(dir: Path, throwOnFailure: Boolean): List<Path>? {
    if (!exists(dir)) {
      if (throwOnFailure) throw IOException("not a directory: $dir")
      return null
    }
    return (files.keys.asSequence() + directories.asSequence())
      .filter { it.getParent() == dir }
      .sorted()
      .toList()
  }

  override fun openReadOnly(file: Path): FileHandle {
    val bytes = files[file] ?: throw FileNotFoundException("no such file: $file")
    return MemoryFileHandle(false, bytes)
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    if (mustCreate && file in files) throw IOException("$file already exists.")
    if (mustExist && file !in files) throw FileNotFoundException("no such file: $file")
    return MemoryFileHandle(true, files[file] ?: ByteArray(0))
      .also { handle -> openHandles[file] = handle }
  }

  override fun source(file: Path): Source {
    val bytes = files[file] ?: throw FileNotFoundException("no such file: $file")
    return Okio.source(ByteArrayInputStream(bytes))
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    if (mustCreate && file in files) throw IOException("$file already exists.")
    return object : Sink {
      val out = ByteArrayOutputStream()
      override fun write(source: Buffer, byteCount: Long) {
        out.write(source.readByteArray(byteCount))
      }

      override fun flush() {
      }

      override fun timeout(): Timeout = Timeout.NONE

      override fun close() {
        files[file] = out.toByteArray()
      }
    }
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    if (mustExist && file !in files) throw FileNotFoundException("no such file: $file")
    val existing = files[file] ?: ByteArray(0)
    return object : Sink {
      val out = ByteArrayOutputStream().apply { write(existing) }
      override fun write(source: Buffer, byteCount: Long) {
        out.write(source.readByteArray(byteCount))
      }

      override fun flush() {
      }

      override fun timeout(): Timeout = Timeout.NONE

      override fun close() {
        files[file] = out.toByteArray()
      }
    }
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean) {
    if (dir in directories || dir in files) {
      if (mustCreate) throw IOException("$dir already exists.")
      return
    }
    directories.add(dir)
  }

  override fun atomicMove(source: Path, target: Path) {
    files[target] = files.remove(source)
      ?: throw FileNotFoundException("no such file: $source")
  }

  override fun delete(path: Path, mustExist: Boolean) {
    if (files.remove(path) != null) return
    if (directories.remove(path)) return
    if (mustExist) throw FileNotFoundException("no such file: $path")
  }

  override fun createSymlink(source: Path, target: Path) {
    throw IOException("unsupported")
  }

  /** Captures written bytes so the Apicurio openReadWrite/write/close pattern is observable. */
  private val openHandles = mutableMapOf<Path, FileHandle>()

  private inner class MemoryFileHandle(
    readWrite: Boolean,
    bytes: ByteArray,
  ) : FileHandle(readWrite) {
    private var data = bytes

    override fun protectedRead(
      fileOffset: Long,
      array: ByteArray,
      arrayOffset: Int,
      byteCount: Int,
    ): Int {
      if (fileOffset >= data.size) return -1
      val toCopy = Math.min(byteCount.toLong(), data.size - fileOffset).toInt()
      System.arraycopy(data, fileOffset.toInt(), array, arrayOffset, toCopy)
      return toCopy
    }

    override fun protectedWrite(
      fileOffset: Long,
      array: ByteArray,
      arrayOffset: Int,
      byteCount: Int,
    ) {
      val end = fileOffset + byteCount
      if (end > data.size) {
        data = data.copyOf(end.toInt())
      }
      System.arraycopy(array, arrayOffset, data, fileOffset.toInt(), byteCount)
    }

    override fun protectedFlush() {
    }

    override fun protectedResize(size: Long) {
      data = data.copyOf(size.toInt())
    }

    override fun protectedSize(): Long = data.size.toLong()

    override fun protectedClose() {
      if (readWrite) {
        files[currentPath()] = data
      }
    }

    private fun currentPath(): Path {
      return openHandles.entries.firstOrNull { it.value === this }?.key
        ?: throw IllegalStateException("handle is not registered")
    }
  }
}
