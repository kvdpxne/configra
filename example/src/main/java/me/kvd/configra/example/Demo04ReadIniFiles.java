package me.kvd.configra.example;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import me.kvd.configra.core.ConfigFile;
import me.kvd.configra.format.ini.IniFormat;

/**
 * Reads the two INI files produced by Demos 01 and 02. Demonstrates format
 * autodetection, per-key lookup and bulk {@code peekAll}.
 */
public final class Demo04ReadIniFiles {

  private Demo04ReadIniFiles() {
    throw new AssertionError("No instances.");
  }

  public static void run(final Path ini1, final Path ini2) throws IOException {
    System.out.println("--- Reading " + ini1.getFileName() + " ---");
    // Format auto-detected from the .ini extension via ServiceLoader.
    final ConfigFile first = ConfigFile.load(ini1);
    System.out.println("format       = " + first.format().name());
    System.out.println("keys         = " + first.keys());
    System.out.println("general.mode = "
      + first.find("general.mode").orElse("(absent)"));
    System.out.println("network.port = "
      + first.find("network.port").orElse("(absent)"));
    System.out.println("spoof.plugins = "
      + first.find("spoof.plugins").orElse("(absent)"));

    System.out.println();
    System.out.println("--- Reading " + ini2.getFileName() + " ---");
    final ConfigFile second = ConfigFile.load(ini2, IniFormat.INSTANCE);
    System.out.println("keys         = " + second.keys());
    System.out.println("spoof.plugins = "
      + second.find("spoof.plugins").orElse("(absent)"));
    System.out.println("notifications.window = "
      + second.find("notifications.window").orElse("(absent)"));

    // Bulk peek: one file pass for many keys.
    System.out.println();
    final List<String> wanted = List.of(
      "general.mode", "network.port", "spoof.plugins",
      "does.not.exist");
    final Map<String, String> peeked = ConfigFile.peekAll(ini2, wanted);
    System.out.println("peekAll(" + wanted + ")");
    peeked.forEach((k, v) -> System.out.println("  " + k + " = " + v));
  }
}