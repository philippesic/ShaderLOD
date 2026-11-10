package foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class BoundaryTest {
  static List<String> violations(JavaClasses classes) {
    List<String> errors = new ArrayList<>();
    for (var type : classes) {
      var owner = type.getPackageName();
      for (var edge : type.getDirectDependenciesFromSelf()) {
        var target = edge.getTargetClass().getPackageName();
        if (owner.startsWith("foundation.contracts")
            && !target.startsWith("java.")
            && !target.startsWith("javax.")
            && !target.startsWith("foundation.contracts")) {
          errors.add("T-ARCH");
        }
        if (owner.startsWith("foundation.main") && target.startsWith("foundation.dev")) {
          errors.add("T-ARCH");
        }
        if (owner.contains(".providers.")
            && target.contains(".providers.")
            && !owner.equals(target)) {
          errors.add("D2");
        }
      }
      for (var call : type.getMethodCallsFromSelf()) {
        String target = call.getTarget().getOwner().getName();
        String method = call.getTarget().getName();
        if ((target.equals("java.nio.file.Files")
                && method.matches(
                    "write.*|newBufferedWriter|newOutputStream|copy|move|delete.*|createFile"))
            || target.matches(
                "java.io.(FileOutputStream|FileWriter|RandomAccessFile|PrintWriter|PrintStream|BufferedWriter|OutputStreamWriter|DataOutputStream|ObjectOutputStream)")) {
          if (!owner.equals("foundation.publisher")) {
            errors.add("D3");
          }
        }
        if (target.equals("java.io.File") && method.matches("create.*|delete.*|mkdir.*|set.*")) {
          if (!owner.equals("foundation.publisher")) {
            errors.add("D3");
          }
        }
        if ((target.equals("java.lang.Runtime") && method.equals("exec"))
            || (target.equals("java.lang.ProcessBuilder") && method.equals("start"))) {
          errors.add("D6d");
        }
        if ((target.equals("java.lang.System")
                && method.matches("getenv|getPropert.*|setPropert.*"))
            || (target.matches("java.lang.(Boolean|Integer|Long)")
                && method.matches("getBoolean|getInteger|getLong"))) {
          if (!owner.equals("foundation.config")) {
            errors.add("D6b");
          }
        }
        if (method.matches(
                "gl(Finish|FenceSync|BeginQuery.*|EndQuery.*|GetQuery.*|QueryCounter|ClientWaitSync|WaitSync)")
            && !owner.equals("foundation.measurement")) {
          errors.add("D6c");
        }
      }
      for (var call : type.getConstructorCallsFromSelf()) {
        if (call.getTarget()
                .getOwner()
                .getName()
                .matches(
                    "java.io.(FileOutputStream|FileWriter|RandomAccessFile|PrintWriter|PrintStream|BufferedWriter|OutputStreamWriter|DataOutputStream|ObjectOutputStream)")
            && !owner.equals("foundation.publisher")) {
          errors.add("D3");
        }
      }
    }
    return errors;
  }

  private List<String> specimen(String pack, String body) throws IOException {
    Path dir = Files.createTempDirectory("shaderlod-bytecode-");
    try {
      Path source = dir.resolve("Probe.java");
      Files.writeString(source, "package " + pack + "; public class Probe { " + body + " }");
      Path other = dir.resolve("Other.java");
      Files.writeString(other, "package foundation.providers.b; public class Other {}");
      int exit =
          ToolProvider.getSystemJavaCompiler()
              .run(null, null, null, "-d", dir.toString(), source.toString(), other.toString());
      assertEquals(0, exit, "S2: specimen failed compilation");
      return violations(new ClassFileImporter().importPath(dir));
    } finally {
      try (var paths = Files.walk(dir)) {
        for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
    }
  }

  @Test
  void checkedBytecode() {
    Path root = Path.of(System.getProperty("foundation.root"), "build/classes/java");
    JavaClasses classes =
        new ClassFileImporter()
            .importPaths(
                java.util.stream.Stream.of("benchmarkCore", "main", "devClient")
                    .map(root::resolve)
                    .filter(Files::exists)
                    .toList());
    assertFalse(classes.isEmpty(), "S2: zero contract classes");
    assertEquals(List.of(), violations(classes));
  }

  @Test
  void d3Writer() throws IOException {
    String body =
        "void save(java.nio.file.Path p) throws java.io.IOException { java.nio.file.Files.writeString(p, \"data\"); }";
    assertTrue(specimen("foundation.providers.a", body).contains("D3"));
    assertEquals(List.of(), specimen("foundation.publisher", body));
  }

  @Test
  void d6bSettings() throws IOException {
    String body = "String read() { return System.getenv(\"HOME\"); }";
    assertTrue(specimen("foundation.timer", body).contains("D6b"));
    assertEquals(List.of(), specimen("foundation.config", body));
  }

  @Test
  void d6cGpu() throws IOException {
    String body = "static class GL { static void glFinish() {} } void run() { GL.glFinish(); }";
    assertTrue(specimen("foundation.providers.a", body).contains("D6c"));
    assertEquals(List.of(), specimen("foundation.measurement", body));
  }

  @Test
  void d2Providers() throws IOException {
    assertTrue(
        specimen("foundation.providers.a", "foundation.providers.b.Other value() { return null; }")
            .contains("D2"));
    assertEquals(
        List.of(),
        specimen(
            "foundation.providers.a",
            "java.time.Instant value() { return java.time.Instant.EPOCH; }"));
  }

  @Test
  void d3PrintWriter() throws IOException {
    String body =
        "void save() throws java.io.IOException { try (var writer = new java.io.PrintWriter(\"summary.json\")) { writer.print(1); } }";
    assertTrue(specimen("foundation.providers.a", body).contains("D3"));
    assertEquals(List.of(), specimen("foundation.publisher", body));
  }

  @Test
  void d6dLauncher() throws IOException {
    assertTrue(
        specimen(
                "foundation.report",
                "void launch() throws java.io.IOException { new ProcessBuilder(\"minecraft\").start(); }")
            .contains("D6d"));
    assertEquals(List.of(), specimen("foundation.report", "int helper() { return 1; }"));
  }

  @Test
  void architecture() throws IOException {
    assertTrue(
        specimen("foundation.contracts", "foundation.providers.b.Other read() { return null; }")
            .contains("T-ARCH"));
    assertEquals(
        List.of(),
        specimen(
            "foundation.contracts", "javax.xml.parsers.DocumentBuilder read() { return null; }"));
  }
}
