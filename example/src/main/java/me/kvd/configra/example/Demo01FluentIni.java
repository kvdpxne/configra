package me.kvd.configra.example;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import me.kvd.configra.PropertyKey;
import me.kvd.configra.core.ConfigFile;
import me.kvd.configra.core.ConfigBuilder;
import me.kvd.configra.format.ini.IniFormat;
import me.kvd.configra.options.WriteOptions;

/**
 * Creates an INI file with the fluent {@link ConfigBuilder} — no enums,
 * keys declared inline. Demonstrates sections, comments and typed values.
 */
public final class Demo01FluentIni {

  private Demo01FluentIni() {
    throw new AssertionError("No instances.");
  }

  public static Path run(final Path dir) throws IOException {
    final Path file = dir.resolve("demo01-fluent.ini");

    final ConfigFile config = new ConfigBuilder()
      .header("Demo 01 — Fluent INI builder.",
        "No enums. PropertyKey instances created inline.",
        "File: demo01-fluent.ini")
      .blank()
      .section("general")
      .comment("; Available modes: MINECRAFT, CUSTOM, DISABLED.")
      .set(PropertyKey.string("mode", "MINECRAFT"), "MINECRAFT")
      .comment("; Verbose logging. Enable only for diagnostics.")
      .set(PropertyKey.bool("debug", false), true)
      .endSection()
      .blank()
      .section("network")
      .comment("; Interface to bind.")
      .set(PropertyKey.string("host", "0.0.0.0"), "0.0.0.0")
      .comment("; TCP port.")
      .set(PropertyKey.integer("port", 25565), 25565)
      .endSection()
      .blank()
      .section("spoof")
      .comment("; Serve a fake plugin list instead of an error.")
      .set(PropertyKey.bool("enabled", true), true)
      .comment("; Fake names, comma-separated.")
      .set(PropertyKey.stringList("plugins"), List.of("Alpha", "Beta", "Gamma"))
      .endSection()
      .build(IniFormat.INSTANCE);

    config.save(file, WriteOptions.ini());
    return file;
  }
}