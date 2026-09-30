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
    System.out.println("placeholder-consumer-ok");
  }

  private PlaceholderConsumerMain() {
  }
}
