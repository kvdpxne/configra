package me.kvd.properties.schema;

import java.util.Objects;
import me.kvd.properties.ConfigKeyDeclaration;
import me.kvd.properties.PropertyKey;
import me.kvd.properties.core.ConfigBuilder;
import me.kvd.properties.core.ConfigFormats;
import me.kvd.properties.spi.ConfigFormat;

/**
 * Builds default {@link ConfigBuilder} instances from a
 * {@link ConfigKeyDeclaration} enum.
 *
 * <p>Separated from {@link EnumSchema} so that the schema class focuses on
 * validation and iteration, while this class owns layout decisions. Both
 * accept the same enum, and callers pick the entry point that matches their
 * intent:</p>
 *
 * <pre>{@code
 * // Just the defaults, flat (.properties style):
 * final ConfigFile defaults = EnumDefaults.flat(EnumSchema.of(Keys.class))
 *         .build(ConfigFormat.PROPERTIES);
 *
 * // Defaults adapted to INI, dotted keys become sections:
 * final ConfigFile ini = EnumDefaults.of(EnumSchema.of(Keys.class))
 *         .build(ConfigFormat.INI);
 * }</pre>
 *
 * <h2>Layout rules</h2>
 * <ul>
 *   <li><b>Flat</b> ({@code .properties}): every key is written verbatim,
 *       dots included. Comment marker is {@code '#'}.</li>
 *   <li><b>Sectioned</b> (INI): a dotted key {@code section.key} is split
 *       into {@code builder.section("section").set("key", …)}. Keys without
 *       a dot stay global. Comment marker is {@code ';'}.</li>
 *   <li>Comments from {@link ConfigKeyDeclaration#comment()} are inserted
 *       above each key, one node per line.</li>
 *   <li>Only keys with a default are emitted; required keys without a
 *       default are skipped (validation is {@link EnumSchema}'s job).</li>
 * </ul>
 */
public final class EnumDefaults<E extends Enum<E> & ConfigKeyDeclaration> {

  private final EnumSchema<E> schema;

  private EnumDefaults(final EnumSchema<E> schema) {
    this.schema = schema;
  }

  /**
   * Creates a defaults builder bound to {@code schema}. The format is
   * chosen later via {@link #flat()} / {@link #sectioned()} or directly
   * via {@link #build(ConfigFormat)}.
   */
  public static <E extends Enum<E> & ConfigKeyDeclaration> EnumDefaults<E> of(
    final EnumSchema<E> schema) {
    Objects.requireNonNull(schema, "schema");
    return new EnumDefaults<>(schema);
  }

  /**
   * Convenience: {@code EnumDefaults.of(EnumSchema.of(type))}.
   */
  public static <E extends Enum<E> & ConfigKeyDeclaration> EnumDefaults<E> of(
    final Class<E> type) {
    return of(EnumSchema.of(type));
  }

  /**
   * Returns a builder adapted to {@code format}. INI dialects get sectioned
   * layout; everything else is flat.
   */
  public ConfigBuilder build(final ConfigFormat format) {
    Objects.requireNonNull(format, "format");
    final boolean ini = ConfigFormats.isIni(format);
    final ConfigBuilder builder = new ConfigBuilder()
      .commentChar(ini ? ';' : '#');

    String currentSection = null;
    for (final E constant : this.schema.constants()) {
      final PropertyKey<?> key = constant.key();
      final String fullName = key.name();

      final String section;
      final String localKey;
      if (ini) {
        final int dot = fullName.lastIndexOf('.');
        if (0 < dot && dot < fullName.length() - 1) {
          section = fullName.substring(0, dot);
          localKey = fullName.substring(dot + 1);
        } else {
          section = null;
          localKey = fullName;
        }
      } else {
        section = null;
        localKey = fullName;
      }

      // Open / close sections on transition.
      if (!Objects.equals(currentSection, section)) {
        if (null != currentSection) {
          builder.endSection();
        }
        if (null != section) {
          builder.section(section);
        }
        currentSection = section;
      }

      // Comments from the declaration.
      final String comment = constant.comment();
      if (null != comment && !comment.isBlank()) {
        for (final String line : comment.split("\n")) {
          builder.comment(line);
        }
      }

      // Default value, if any.
      if (key.hasDefault()) {
        setWithLocalKey(builder, localKey, key);
      }
    }
    if (null != currentSection) {
      builder.endSection();
    }
    return builder;
  }

  // ----- Internals -----

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void setWithLocalKey(final ConfigBuilder builder,
                                      final String localKey,
                                      final PropertyKey key) {
    // Rebuild the key with the local name so ConfigBuilder stores it
    // under the section it is currently in.
    builder.set(
      new PropertyKey<>(localKey, key.codec(), key.defaultValue()),
      key.defaultValue());
  }
}