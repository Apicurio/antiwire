import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Java 11 bytecode gate for jars (TASK-2 AC#3, DEC-3).
 *
 * <p>Builds, for every jar, the class set a Java 11 JVM would actually select: base entries,
 * overridden by META-INF/versions/N entries when the manifest declares Multi-Release: true
 * and N is 11 or lower; version subtrees above 11 are ignored because Java 11 never loads
 * them. Fails when any selected class file has a major version above 55 (Java 11). Own
 * module jars compiled with --release 11 and future production dependency jars (TASK-4)
 * are both checked this way.
 *
 * <p>Bytecode major version plus the real-JVM consumer smoke together carry the Java 11
 * enforcement: --release 11 already pins API usage for the port's own sources, and
 * dependency API usage is exercised at runtime by the Java 11 consumer check, which TASK-5
 * extends beyond the placeholder.
 *
 * <p>Usage: java scripts/Java11BytecodeCheck.java [--jar FILE]... [--dep-cp FILE]...
 *
 * <p>--jar marks a module jar (its absence is an error); --dep-cp marks a resolved runtime
 * classpath file whose jar entries are production dependencies. Reactor peers resolved to
 * their jars are recognized and counted only once, as the module jars they are.
 *
 * <p>Exit codes: 0 ok, 1 violations or missing input, 2 unreadable input.
 */
public final class Java11BytecodeCheck {

  private static final int JAVA11_MAJOR = 55;

  public static void main(String[] args) throws IOException {
    List<Path> moduleJars = new ArrayList<>();
    List<Path> depJars = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--jar":
          moduleJars.add(Paths.get(args[++i]));
          break;
        case "--dep-cp":
          depJars.addAll(jarsFromCpFile(Paths.get(args[++i])));
          break;
        default:
          System.err.println("error: unknown argument " + args[i]);
          System.exit(1);
      }
    }
    if (moduleJars.isEmpty()) {
      System.err.println("error: at least one --jar is required");
      System.exit(1);
    }
    for (Path jar : moduleJars) {
      if (!Files.isRegularFile(jar)) {
        System.out.println("MISSING module jar: " + jar + " (build the modules first)");
        System.exit(1);
      }
    }

    // Reactor peers resolved to their jars appear in the exported classpaths; they are the
    // module jars already checked, not external production dependencies. The same external
    // dependency jar also shows up on several modules' exported classpaths; deduplicate by
    // real path so each dependency is scanned once (module jars stay per-module).
    java.util.Set<Path> moduleJarPaths = new java.util.HashSet<>();
    for (Path jar : moduleJars) {
      moduleJarPaths.add(jar.toRealPath());
    }
    java.util.LinkedHashSet<Path> uniqueDepPaths = new java.util.LinkedHashSet<>();
    for (Path jar : depJars) {
      Path real;
      try {
        real = jar.toRealPath();
      } catch (IOException unreadable) {
        real = jar;
      }
      if (!moduleJarPaths.contains(real)) {
        uniqueDepPaths.add(real);
      }
    }
    depJars = new ArrayList<>(uniqueDepPaths);

    System.out.println("module jars checked: " + moduleJars.size());
    System.out.println("production dependency jars checked: " + depJars.size()
        + (depJars.isEmpty()
            ? " (none declared; the empty set is reported explicitly, not silently skipped)"
            : ""));

    List<String> violations = new ArrayList<>();
    Map<Integer, Integer> majors = new TreeMap<>();
    int classesChecked = 0;
    for (boolean isModule : new boolean[] {true, false}) {
      for (Path jar : isModule ? moduleJars : depJars) {
        Result r = checkJar(jar);
        classesChecked += r.classesChecked;
        r.majors.forEach((major, count) -> majors.merge(major, count, Integer::sum));
        for (String v : r.violations) {
          violations.add(jar.getFileName() + "!" + v);
        }
      }
    }

    System.out.println("classes checked: " + classesChecked + ", major versions seen: " + majors);
    if (violations.isEmpty()) {
      System.out.println("bytecode-java11 OK: every class Java 11 can select has major <="
          + " " + JAVA11_MAJOR);
      System.exit(0);
    }
    for (String v : violations) {
      System.out.println("VIOLATION " + v);
    }
    System.out.println("FAIL: " + violations.size() + " class file(s) exceed major version "
        + JAVA11_MAJOR + " in the Java 11 view of the checked jars");
    System.exit(1);
  }

  private static final class Result {
    int classesChecked;
    List<String> violations = new ArrayList<>();
    Map<Integer, Integer> majors = new TreeMap<>();
  }

  private static Result checkJar(Path jar) throws IOException {
    Result result = new Result();
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      boolean multiRelease = false;
      ZipEntry manifestEntry = zip.getEntry("META-INF/MANIFEST.MF");
      if (manifestEntry != null) {
        try (InputStream in = zip.getInputStream(manifestEntry)) {
          multiRelease = Boolean.parseBoolean(
              new Manifest(in).getMainAttributes().getValue("Multi-Release"));
        }
      }

      // name -> selected zip entry; apply base first, then versioned overrides ascending so
      // the highest version not above 11 wins.
      Map<String, ZipEntry> selected = new LinkedHashMap<>();
      Map<String, Integer> versionedOnly = new HashMap<>();
      for (ZipEntry e : Collections.list(zip.entries())) {
        String name = e.getName();
        if (e.isDirectory()) {
          continue;
        }
        if (name.startsWith("META-INF/versions/")) {
          if (!multiRelease) {
            continue; // not a versioned jar for Java 11: subtree invisible to class loading
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
          String baseName = name.substring(slash + 1);
          if (version > 11) {
            continue; // Java 11 never selects it
          }
          Integer seen = versionedOnly.get(baseName);
          if (seen == null || version > seen) {
            versionedOnly.put(baseName, version);
            selected.put(baseName, e);
          }
        } else {
          selected.putIfAbsent(name, e);
        }
      }

      for (Map.Entry<String, ZipEntry> e : selected.entrySet()) {
        String name = e.getKey();
        if (!name.endsWith(".class") || name.equals("module-info.class")) {
          continue;
        }
        byte[] header;
        try (InputStream in = zip.getInputStream(e.getValue())) {
          header = in.readNBytes(8);
        }
        if (header.length < 8) {
          result.violations.add(name + " (too short to be a class file)");
          continue;
        }
        int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        result.classesChecked++;
        result.majors.merge(major, 1, Integer::sum);
        if (major > JAVA11_MAJOR) {
          result.violations.add(name + " (major " + major + ")");
        }
      }
    }
    return result;
  }

  private static List<Path> jarsFromCpFile(Path cpFile) throws IOException {
    List<Path> jars = new ArrayList<>();
    if (!Files.isRegularFile(cpFile)) {
      System.out.println("MISSING classpath export: " + cpFile + " (run mvn verify first)");
      System.exit(2);
    }
    for (String entry : Files.readString(cpFile).split(File.pathSeparator)) {
      String trimmed = entry.trim();
      if (trimmed.endsWith(".jar")) {
        jars.add(Paths.get(trimmed));
      }
    }
    return jars;
  }
}
