import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Class-origin and duplicate-class check tool (TASK-2 AC#4).
 *
 * <p>Given one module's resolved test classpath (exported by maven-dependency-plugin to
 * target/classpath-test.txt), this tool:
 *
 * <ul>
 *   <li>scans every classpath artifact (jar or directory) for classes under the retained-name
 *       prefixes (com/squareup/wire/ and okio/ per docs/compatibility-matrix.md section G) and
 *       prints each class name with the artifact it comes from; jar scanning is multi-release
 *       aware with the same selection rule as Java11BytecodeCheck: base entries, overridden by
 *       the highest META-INF/versions/N entry at or below the running JVM's feature version
 *       when the manifest declares Multi-Release: true;
 *   <li>fails with exit code 2 when the same class name resolves from two different
 *       artifacts, reporting both origins (duplicate-class prevention rules);
 *   <li>loads the configurable class list (config/class-origins.txt) through a class loader
 *       over that classpath and prints each class's CodeSource, so TASK-5 and TASK-6 can show
 *       where port-under-test classes load from with upstream implementations isolated.
 * </ul>
 *
 * <p>Usage: java scripts/ClassOriginCheck.java --label MODULE --classpath-file FILE
 * [--prefix PREFIX]... [--load-list FILE]
 *
 * <p>Exit codes: 0 ok, 1 usage or input error, 2 duplicate class name, 3 linkage failure.
 */
public final class ClassOriginCheck {

  public static void main(String[] args) throws Exception {
    String label = null;
    String classpathFile = null;
    String loadList = null;
    List<String> prefixes = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--label":
          label = args[++i];
          break;
        case "--classpath-file":
          classpathFile = args[++i];
          break;
        case "--prefix":
          prefixes.add(args[++i]);
          break;
        case "--load-list":
          loadList = args[++i];
          break;
        default:
          usage("unknown argument: " + args[i]);
      }
    }
    if (label == null || classpathFile == null || prefixes.isEmpty()) {
      usage("--label, --classpath-file and at least one --prefix are required");
    }

    List<Path> entries = new ArrayList<>();
    for (String entry : Files.readString(Paths.get(classpathFile)).split(File.pathSeparator)) {
      String trimmed = entry.trim();
      if (!trimmed.isEmpty()) {
        entries.add(Paths.get(trimmed));
      }
    }

    // class name -> artifact origins (canonical paths), sorted for stable output.
    Map<String, TreeSet<String>> originsByClass = new TreeMap<>();
    int artifactsScanned = 0;
    for (Path entry : entries) {
      String origin = entry.toRealPath().toString();
      if (Files.isDirectory(entry)) {
        try (Stream<Path> walk = Files.walk(entry)) {
          for (Path p : (Iterable<Path>) walk::iterator) {
            String rel = entry.relativize(p).toString().replace(File.separatorChar, '/');
            if (!rel.endsWith(".class") || !matches(rel, prefixes)) {
              continue;
            }
            originsByClass.computeIfAbsent(className(rel), k -> new TreeSet<>()).add(origin);
          }
        }
        artifactsScanned++;
      } else if (Files.isRegularFile(entry) && entry.getFileName().toString().endsWith(".jar")) {
        scanJar(entry, origin, prefixes, originsByClass);
        artifactsScanned++;
      }
    }

    System.out.println("[" + label + "] artifacts scanned: " + artifactsScanned);
    for (Map.Entry<String, TreeSet<String>> e : originsByClass.entrySet()) {
      System.out.println("[" + label + "] " + e.getKey() + " <- " + String.join(" ; ", e.getValue()));
    }

    List<String> duplicates = new ArrayList<>();
    for (Map.Entry<String, TreeSet<String>> e : originsByClass.entrySet()) {
      if (e.getValue().size() > 1) {
        duplicates.add(e.getKey() + " resolves from " + e.getValue().size() + " artifacts: "
            + String.join(" ; ", e.getValue()));
      }
    }
    System.out.println("[" + label + "] port classes found: " + originsByClass.size()
        + ", duplicate class names: " + duplicates.size());
    if (!duplicates.isEmpty()) {
      for (String d : duplicates) {
        System.out.println("DUPLICATE " + d);
      }
      System.out.println("[" + label + "] FAIL: duplicate port class names on the classpath"
          + " (docs/compatibility-matrix.md, duplicate-class prevention rules)");
      System.exit(2);
    }

    if (loadList != null) {
      URL[] urls = new URL[entries.size()];
      for (int i = 0; i < entries.size(); i++) {
        urls[i] = entries.get(i).toUri().toURL();
      }
      // Null parent: report exactly what these artifacts serve, not what the JVM already has.
      try (URLClassLoader loader = new URLClassLoader(urls, null)) {
        int loaded = 0;
        for (String line : Files.readAllLines(Paths.get(loadList))) {
          String name = line.trim();
          if (name.isEmpty() || name.startsWith("#")) {
            continue;
          }
          try {
            Class<?> c = Class.forName(name, false, loader);
            Object codeSource = c.getProtectionDomain().getCodeSource();
            System.out.println("[" + label + "] loads " + name + " from " + codeSource);
            loaded++;
          } catch (ClassNotFoundException notOnThisClasspath) {
            System.out.println("[" + label + "] " + name
                + " not present on this module's classpath (informational)");
          }
        }
        System.out.println("[" + label + "] configured classes resolved here: " + loaded);
      } catch (LinkageError e) {
        System.out.println("[" + label + "] FAIL: linkage failure: " + e);
        System.exit(3);
      }
    }
  }

  /**
   * Adds the jar's effective classes to the origins map when they match a retained prefix,
   * applying the multi-release override exactly as Java11BytecodeCheck does: base entries
   * first, then the highest META-INF/versions/N entry at or below the running JVM's feature
   * version wins when the manifest declares Multi-Release: true. The effective class is the
   * base entry plus that highest override, so a class shipped only under META-INF/versions/
   * is still attributed to its jar and still participates in duplicate detection.
   */
  private static void scanJar(Path jarPath, String origin, List<String> prefixes,
      Map<String, TreeSet<String>> originsByClass) throws IOException {
    boolean multiRelease = false;
    // Effective resource name -> version that selected it (0 for a base entry).
    Map<String, Integer> selectedVersion = new HashMap<>();
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      ZipEntry manifestEntry = jar.getEntry("META-INF/MANIFEST.MF");
      if (manifestEntry != null) {
        try (InputStream in = jar.getInputStream(manifestEntry)) {
          multiRelease = Boolean.parseBoolean(
              new Manifest(in).getMainAttributes().getValue("Multi-Release"));
        }
      }
      int runtimeFeature = Runtime.version().feature();
      for (ZipEntry e : Collections.list(jar.entries())) {
        String name = e.getName();
        if (e.isDirectory() || !name.endsWith(".class")) {
          continue;
        }
        if (name.startsWith("META-INF/versions/")) {
          if (!multiRelease) {
            continue; // not a versioned jar: the subtree is invisible to class loading
          }
          int slash = name.indexOf('/', "META-INF/versions/".length());
          if (slash < 0) {
            continue;
          }
          int version;
          try {
            version = Integer.parseInt(name.substring("META-INF/versions/".length(), slash));
          } catch (NumberFormatException notANumber) {
            continue;
          }
          if (version > runtimeFeature) {
            continue; // the running JVM never selects it
          }
          String baseName = name.substring(slash + 1);
          Integer seen = selectedVersion.get(baseName);
          if (seen == null || version > seen) {
            selectedVersion.put(baseName, version);
          }
        } else {
          selectedVersion.putIfAbsent(name, 0); // a versioned override wins later
        }
      }
    }
    for (String name : selectedVersion.keySet()) {
      if (matches(name, prefixes)) {
        originsByClass.computeIfAbsent(className(name), k -> new TreeSet<>()).add(origin);
      }
    }
  }

  private static boolean matches(String resourcePath, List<String> prefixes) {
    for (String prefix : prefixes) {
      if (resourcePath.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static String className(String resourcePath) {
    return resourcePath.substring(0, resourcePath.length() - ".class".length()).replace('/', '.');
  }

  private static void usage(String problem) {
    System.err.println("error: " + problem);
    System.err.println("usage: ClassOriginCheck --label MODULE --classpath-file FILE"
        + " [--prefix PREFIX]... [--load-list FILE]");
    System.exit(1);
  }
}
