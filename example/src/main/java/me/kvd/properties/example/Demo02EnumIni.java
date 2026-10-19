package me.kvd.properties.example;

import java.io.IOException;
import java.nio.file.Path;
import me.kvd.properties.core.ConfigFile;
import me.kvd.properties.example.keys.ServerKeys;
import me.kvd.properties.format.ini.IniFormat;
import me.kvd.properties.options.WriteOptions;
import me.kvd.properties.schema.EnumDefaults;

/**
 * Creates an INI file from an enum schema. Every key in {@link ServerKeys}
 * declares a fully qualified name ({@code section.key}), a default and one
 * or more comment lines; {@link EnumIniBuilder} assembles the file.
 */
public final class Demo02EnumIni {

  private Demo02EnumIni() {
    throw new AssertionError("No instances.");
  }

  public static Path run(final Path dir) throws IOException {
    final Path file = dir.resolve("demo02-enum.ini");

    final ConfigFile config = EnumDefaults.of(ServerKeys.class)
      .build(IniFormat.INSTANCE)
      .header("Demo 02 — Enum-driven INI.",
        "Keys declared in ServerKeys, split into sections.",
        "File: demo02-enum.ini")
      .build(IniFormat.INSTANCE);

    config.save(file, WriteOptions.ini());
    return file;
  }
}