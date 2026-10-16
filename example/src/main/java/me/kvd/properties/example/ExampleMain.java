package me.kvd.properties.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Runs every demo in order and prints the result to stdout. The work
 * directory is created fresh under the system temp dir; it is deleted on
 * exit unless {@code --keep} is passed as the first argument.
 */
public final class ExampleMain {

  private ExampleMain() {
    throw new AssertionError("No instances.");
  }

  public static void main(final String[] args) throws Exception {
    final boolean keep = 0 < args.length && "--keep".equals(args[0]);
    final Path workDir = Files.createTempDirectory("properties-example");

    System.out.println("Work directory: " + workDir);

    try {
      banner("Demo 1 — Fluent INI builder (no enums)");
      final Path ini1 = Demo01FluentIni.run(workDir);
      printFile(ini1);

      banner("Demo 2 — Enum-driven INI");
      final Path ini2 = Demo02EnumIni.run(workDir);
      printFile(ini2);

      banner("Demo 3 — Read .properties from classpath");
      Demo03ReadPropertiesResource.run();

      banner("Demo 4 — Read earlier INI files");
      Demo04ReadIniFiles.run(ini1, ini2);

      banner("Demo 5 — Operations on INI");
      Demo05Operations.run(workDir, ini1);
    } finally {
      if (!keep) {
        deleteRecursively(workDir);
        System.out.println();
        System.out.println("Cleaned up " + workDir);
      } else {
        System.out.println();
        System.out.println("Kept " + workDir);
      }
    }
  }

  private static void banner(final String title) {
    System.out.println();
    System.out.println("=".repeat(72));
    System.out.println("= " + title);
    System.out.println("=".repeat(72));
  }

  private static void printFile(final Path path) throws IOException {
    System.out.println("--- " + path.getFileName() + " ---");
    System.out.print(Files.readString(path));
    System.out.println();
  }

  private static void deleteRecursively(final Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var stream = Files.walk(root)) {
      stream.sorted(Comparator.reverseOrder())
        .forEach(path -> {
          try {
            Files.deleteIfExists(path);
          } catch (final IOException ignored) {
            // Best-effort cleanup.
          }
        });
    }
  }
}