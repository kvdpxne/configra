package me.kvd.configra.example;

import java.io.IOException;
import java.nio.file.Path;
import me.kvd.configra.core.ConfigFile;
import me.kvd.configra.example.keys.ServerKeys;
import me.kvd.configra.format.ini.IniFormat;
import me.kvd.configra.options.WriteOptions;
import me.kvd.configra.schema.EnumDefaults;

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