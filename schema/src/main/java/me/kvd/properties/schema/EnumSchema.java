package me.kvd.properties.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.kvd.properties.ConfigKeyDeclaration;
import me.kvd.properties.PropertyKey;
import me.kvd.properties.core.ConfigFile;
import me.kvd.properties.core.PropertiesBuilder;
import me.kvd.properties.exceptions.PropertiesException;

/**
 * Builds defaults, validates presence of required keys, and lists constants
 * from a {@link ConfigKeyDeclaration} enum.
 */
public final class EnumSchema<E extends Enum<E> & ConfigKeyDeclaration> {

  private final Class<E> type;
  private final List<E> constants;

  private EnumSchema(final Class<E> type) {
    this.type = type;
    this.constants = List.of(type.getEnumConstants());
  }

  public static <E extends Enum<E> & ConfigKeyDeclaration> EnumSchema<E> of(
    final Class<E> type) {
    Objects.requireNonNull(type, "type");
    return new EnumSchema<>(type);
  }

  public Class<E> type() {
    return type;
  }

  public List<E> constants() {
    return constants;
  }

  /**
   * Builds a fresh defaults builder with comments above each key.
   */
  public PropertiesBuilder defaults() {
    final PropertiesBuilder builder = new PropertiesBuilder();
    for (final E constant : constants) {
      final String comment = constant.comment();
      if (null != comment && !comment.isBlank()) {
        builder.comment(comment.startsWith("#") || comment.startsWith(";")
          ? comment
          : "# " + comment);
      }
      final PropertyKey<?> key = constant.key();
      if (key.hasDefault()) {
        setDefault(builder, key);
      }
    }
    return builder;
  }

  /**
   * Returns the names of required keys absent from {@code file}.
   */
  public List<String> missing(final ConfigFile file) {
    Objects.requireNonNull(file, "file");
    final List<String> missing = new ArrayList<>();
    for (final E constant : constants) {
      final PropertyKey<?> key = constant.key();
      if (!key.hasDefault() && !file.contains(key)) {
        missing.add(key.name());
      }
    }
    return List.copyOf(missing);
  }

  public void validate(final ConfigFile file) {
    final List<String> missing = missing(file);
    if (!missing.isEmpty()) {
      throw new PropertiesException(
        "Missing required properties: " + missing);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void setDefault(final PropertiesBuilder builder,
                                 final PropertyKey key) {
    builder.set(key, key.defaultValue());
  }
}