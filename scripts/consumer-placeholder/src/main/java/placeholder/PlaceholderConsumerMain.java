package placeholder;

/**
 * Java 11 consumer smoke (named for its TASK-2 origin).
 *
 * <p>It places the module jars on a real Java 11 compile and runtime classpath and exercises
 * ProtoWriter and the loading layer; TASK-21 runs the final published artifacts.
 */
public final class PlaceholderConsumerMain {

  private static final String[] SHELL_CLASSES = {
    "com.squareup.wire.package-info",
    "com.squareup.wire.schema.package-info",
    "com.squareup.wire.java.package-info",
  };

  public static void main(String[] args) throws Exception {
    for (String name : SHELL_CLASSES) {
      Class<?> shellPackage = Class.forName(name, false,
          PlaceholderConsumerMain.class.getClassLoader());
      System.out.println("loaded " + name + " from "
          + shellPackage.getProtectionDomain().getCodeSource().getLocation());
    }

    // TASK-5 AC#7: the consumer exercises the real spike surface on this Java 11 JVM. The
    // expected bytes are the classic varint 150 encoding (96 01) followed by little-endian
    // fixed32 0x01020304 (04 03 02 01); the check script matches the exact output.
    okio.Buffer buffer = new okio.Buffer();
    com.squareup.wire.ProtoWriter writer = new com.squareup.wire.ProtoWriter(buffer);
    writer.writeVarint32(150);
    writer.writeFixed32(0x01020304);
    String hex = buffer.readByteString().hex();
    System.out.println("spike-consumer-ok hex=" + hex);

    // TASK-4 AC#2: the loading layer (okio.Path, okio.FileSystem over java.nio) runs a
    // write/read/metadata/delete round trip on this same Java 11 JVM.
    java.nio.file.Path temp = java.nio.file.Files.createTempFile("antiwire-loading", ".txt");
    okio.Path loadingPath = okio.Path.get(temp.toString());
    try (okio.BufferedSink sink =
        okio.Okio.buffer(okio.FileSystem.SYSTEM.sink(loadingPath, false))) {
      sink.writeUtf8("proto");
    }
    String readBack;
    try (okio.BufferedSource source =
        okio.Okio.buffer(okio.FileSystem.SYSTEM.source(loadingPath))) {
      readBack = source.readUtf8();
    }
    boolean existed = okio.FileSystem.SYSTEM.metadataOrNull(loadingPath) != null;
    okio.FileSystem.SYSTEM.delete(loadingPath, true);
    System.out.println("loading-consumer-ok existed=" + existed + " read=" + readBack);

    System.out.println("placeholder-consumer-ok");
  }

  private PlaceholderConsumerMain() {
  }
}
