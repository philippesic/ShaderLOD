package foundation;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
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
        if ((owner.startsWith("foundation.contracts")
                || owner.startsWith("com.pp.shaderlod.domain"))
            && !target.startsWith("java.")
            && !target.startsWith("javax.")
            && !target.startsWith("foundation.contracts")
            && !target.startsWith("com.pp.shaderlod.domain")) {
          errors.add("T-ARCH");
        }
        if ((owner.startsWith("foundation.main") && target.startsWith("foundation.dev"))
            || (owner.startsWith("com.pp.shaderlod.")
                && !owner.startsWith("com.pp.shaderlod.dev")
                && target.startsWith("com.pp.shaderlod.dev"))) {
          errors.add("T-ARCH");
        }
        if ((owner.startsWith("com.pp.shaderlod.domain")
                && (target.equals("java.io")
                    || target.startsWith("java.nio.file")
                    || target.startsWith("java.net")))
            || (owner.startsWith("com.pp.shaderlod.engine")
                && !target.startsWith("java.")
                && !target.startsWith("com.pp.shaderlod.domain")
                && !target.startsWith("com.pp.shaderlod.engine"))) {
          errors.add("T-ARCH");
        }
        if (owner.startsWith("com.pp.shaderlod.providers.")
            && !target.startsWith("java.")
            && !target.startsWith("com.pp.shaderlod.domain")
            && !target.startsWith("com.pp.shaderlod.engine")
            && !target.startsWith("com.pp.shaderlod.providers.")) {
          errors.add("T-ARCH");
        }
        if (owner.contains(".providers.")
            && target.contains(".providers.")
            && !providerModule(owner).equals(providerModule(target))) {
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
          if (!owner.equals("foundation.publisher")
              && !owner.startsWith("com.pp.shaderlod.storage")) {
            errors.add("D3");
          }
        }
        if (target.equals("java.io.File") && method.matches("create.*|delete.*|mkdir.*|set.*")) {
          if (!owner.equals("foundation.publisher")
              && !owner.startsWith("com.pp.shaderlod.storage")) {
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
          if (!owner.equals("foundation.config") && !owner.equals("com.pp.shaderlod.dev.config")) {
            errors.add("D6b");
          }
        }
        if (method.matches(
                "gl(Finish|FenceSync|BeginQuery.*|EndQuery.*|GetQuery.*|QueryCounter|ClientWaitSync|WaitSync)")
            && !owner.equals("foundation.measurement")
            && !owner.startsWith("com.pp.shaderlod.dev.measurement")) {
          errors.add("D6c");
        }
      }
      for (var call : type.getConstructorCallsFromSelf()) {
        if (call.getTarget()
                .getOwner()
                .getName()
                .matches(
                    "java.io.(FileOutputStream|FileWriter|RandomAccessFile|PrintWriter|PrintStream|BufferedWriter|OutputStreamWriter|DataOutputStream|ObjectOutputStream)")
            && !owner.equals("foundation.publisher")
            && !owner.startsWith("com.pp.shaderlod.storage")) {
          errors.add("D3");
        }
      }
    }
    return errors;
  }

  private static String providerModule(String packageName) {
    int start = packageName.indexOf(".providers.") + ".providers.".length();
    int end = packageName.indexOf('.', start);
    return end < 0 ? packageName : packageName.substring(0, end);
  }

  private List<String> specimen(String pack, String body) throws IOException {
    return specimen(pack, body, "foundation.providers.b");
  }

  private List<String> specimen(String pack, String body, String dependencyPackage)
      throws IOException {
    Path dir = Files.createTempDirectory("shaderlod-bytecode-");
    try {
      Path source = dir.resolve("Probe.java");
      Files.writeString(source, "package " + pack + "; public class Probe { " + body + " }");
      Path other = dir.resolve("Other.java");
      Files.writeString(other, "package " + dependencyPackage + "; public class Other {}");
      Path development = dir.resolve("Development.java");
      Files.writeString(development, "package com.pp.shaderlod.dev; public class Development {}");
      int exit =
          ToolProvider.getSystemJavaCompiler()
              .run(
                  null,
                  null,
                  null,
                  "-d",
                  dir.toString(),
                  source.toString(),
                  other.toString(),
                  development.toString());
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
    for (String role : List.of("domain", "engine", "storage", "integration")) {
      assertTrue(
          classes.stream()
              .anyMatch(type -> type.getPackageName().startsWith("com.pp.shaderlod." + role)),
          "S2: empty production architecture role " + role);
    }
    for (var type : classes) {
      assertTrue(
          type.getPackageName().startsWith("foundation.contracts")
              || List.of("domain", "engine", "storage", "integration", "providers", "dev").stream()
                  .anyMatch(role ->
                      type.getPackageName().equals("com.pp.shaderlod." + role)
                          || type.getPackageName().startsWith("com.pp.shaderlod." + role + ".")),
          "S2: unclassified compiled class " + type.getName());
    }
    slices().matching("com.pp.shaderlod.(*)..").should().beFreeOfCycles().check(classes);
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
    assertEquals(
        List.of(),
        specimen(
            "foundation.providers.a",
            "foundation.providers.a.detail.Other value() { return null; }",
            "foundation.providers.a.detail"));
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
  void productionDomainAndEngineBoundaries() throws IOException {
    assertTrue(
        specimen("com.pp.shaderlod.domain", "java.nio.file.Path value() { return null; }")
            .contains("T-ARCH"));
    assertEquals(
        List.of(),
        specimen(
            "com.pp.shaderlod.domain",
            "java.time.Instant value() { return java.time.Instant.EPOCH; }"));
    assertTrue(
        specimen("com.pp.shaderlod.engine", "foundation.providers.b.Other value() { return null; }")
            .contains("T-ARCH"));
    assertEquals(List.of(), specimen("com.pp.shaderlod.engine", "int value() { return 1; }"));
    assertTrue(
        specimen(
                "com.pp.shaderlod.integration",
                "com.pp.shaderlod.dev.Development value() { return null; }")
            .contains("T-ARCH"));
  }

  @Test
  void providersUseContractsInsteadOfNativeOwners() throws IOException {
    assertTrue(
        specimen(
                "com.pp.shaderlod.providers.one",
                "com.pp.shaderlod.integration.Other value() { return null; }",
                "com.pp.shaderlod.integration")
            .contains("T-ARCH"));
    assertTrue(
        specimen(
                "com.pp.shaderlod.providers.one",
                "com.pp.shaderlod.storage.Other value() { return null; }",
                "com.pp.shaderlod.storage")
            .contains("T-ARCH"));
    assertEquals(
        List.of(),
        specimen(
            "com.pp.shaderlod.providers.one",
            "com.pp.shaderlod.domain.Other value() { return null; }",
            "com.pp.shaderlod.domain"));
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
