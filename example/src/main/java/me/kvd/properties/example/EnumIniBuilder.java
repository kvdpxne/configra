package me.kvd.properties.example;

import java.util.Objects;
import me.kvd.properties.ConfigKeyDeclaration;
import me.kvd.properties.PropertyKey;
import me.kvd.properties.core.ConfigFile;
import me.kvd.properties.core.ConfigBuilder;
import me.kvd.properties.format.ini.IniFormat;
import me.kvd.properties.schema.EnumSchema;

/**
 * Splits enum-declared dotted keys ({@code section.key}) into
 * {@code builder.section("section").set("key", value)} calls, producing a
 * sectioned INI file. Complements {@link EnumSchema#defaults()}, which
 * emits a flat {@code .properties}-style layout.
 */
public final class EnumIniBuilder {

  private EnumIniBuilder() {
    throw new AssertionError("No instances.");
  }

  public static <E extends Enum<E> & ConfigKeyDeclaration> ConfigFile build(
    final EnumSchema<E> schema,
    final String... headerLines) {

    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(headerLines, "headerLines");

    final ConfigBuilder builder = new ConfigBuilder();
    if (0 < headerLines.length) {
      builder.header(headerLines);
      builder.blank();
    }

    String currentSection = null;
    for (final E constant : schema.constants()) {
      final PropertyKey<?> pk = constant.key();
      final String fullName = pk.name();
      final int dot = fullName.lastIndexOf('.');
      final String section = 0 < dot ? fullName.substring(0, dot) : null;
      final String local = 0 < dot ? fullName.substring(dot + 1) : fullName;

      if (!Objects.equals(currentSection, section)) {
        if (null != currentSection) {
          builder.endSection();
        }
        if (null != section) {
          builder.section(section);
        }
        currentSection = section;
      }

      final String comment = constant.comment();
      if (null != comment && !comment.isBlank()) {
        for (final String line : comment.split("\n")) {
          builder.comment("; " + line);
        }
      }

      if (pk.hasDefault()) {
        setUntyped(builder, local, pk);
      }
    }
    if (null != currentSection) {
      builder.endSection();
    }

    return builder.build(IniFormat.INSTANCE);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void setUntyped(final ConfigBuilder builder,
                                 final String localKey,
                                 final PropertyKey key) {
    builder.set(
      new PropertyKey<>(localKey, key.codec(), key.defaultValue()),
      key.defaultValue());
  }
}