package placeholder;

/**
 * TASK-2 consumer smoke placeholder.
 *
 * <p>It proves only that the module jars can be placed on a real Java 11 compile and runtime
 * classpath and that their empty shell classes load from those jars. It certifies NOTHING
 * about Wire compatibility or runtime behavior; TASK-5 replaces the exercised surface with
 * the real spike and TASK-21 runs the final published artifacts.
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

    System.out.println("placeholder-consumer-ok");
  }

  private PlaceholderConsumerMain() {
  }
}
