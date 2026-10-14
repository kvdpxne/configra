package me.kvdpxne.properties;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Typed identifier for a single property. A {@code null}
 * {@linkplain #defaultValue() default} means the property is required.
 *
 * @param <T> the value type
 */
public record PropertyKey<T>(
  String name,
  PropertyCodec<T> codec,
  T defaultValue
) {

  public PropertyKey {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(codec, "codec");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (!name.equals(name.strip())) {
      throw new IllegalArgumentException(
        "name must not have leading or trailing whitespace: '"
          + name + '\'');
    }
    for (int i = 0; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (Character.isISOControl(c)) {
        throw new IllegalArgumentException(
          "name must not contain ISO control characters");
      }
    }
  }

  public boolean hasDefault() {
    return null != defaultValue;
  }

  // ----- Factories -----

  public static PropertyKey<String> string(final String name) {
    return new PropertyKey<>(name, PropertyCodec.STRING, null);
  }

  public static PropertyKey<String> string(final String name,
                                           final String defaultValue) {
    Objects.requireNonNull(defaultValue, "defaultValue");
    return new PropertyKey<>(name, PropertyCodec.STRING, defaultValue);
  }

  public static PropertyKey<Integer> integer(final String name) {
    return new PropertyKey<>(name, PropertyCodec.INTEGER, null);
  }

  public static PropertyKey<Integer> integer(final String name,
                                             final int defaultValue) {
    return new PropertyKey<>(name, PropertyCodec.INTEGER, defaultValue);
  }

  public static PropertyKey<Long> longKey(final String name) {
    return new PropertyKey<>(name, PropertyCodec.LONG, null);
  }

  public static PropertyKey<Long> longKey(final String name,
                                          final long defaultValue) {
    return new PropertyKey<>(name, PropertyCodec.LONG, defaultValue);
  }

  public static PropertyKey<Boolean> bool(final String name) {
    return new PropertyKey<>(name, PropertyCodec.BOOLEAN, null);
  }

  public static PropertyKey<Boolean> bool(final String name,
                                          final boolean defaultValue) {
    return new PropertyKey<>(name, PropertyCodec.BOOLEAN, defaultValue);
  }

  public static PropertyKey<Double> decimal(final String name) {
    return new PropertyKey<>(name, PropertyCodec.DOUBLE, null);
  }

  public static PropertyKey<Double> decimal(final String name,
                                            final double defaultValue) {
    return new PropertyKey<>(name, PropertyCodec.DOUBLE, defaultValue);
  }

  public static PropertyKey<List<String>> stringList(final String name) {
    return new PropertyKey<>(name, PropertyCodec.stringList(), List.of());
  }

  public static PropertyKey<Path> path(final String name) {
    return new PropertyKey<>(name, PropertyCodec.PATH, null);
  }

  public static PropertyKey<Path> path(final String name,
                                       final Path defaultValue) {
    Objects.requireNonNull(defaultValue, "defaultValue");
    return new PropertyKey<>(name, PropertyCodec.PATH, defaultValue);
  }

  public static PropertyKey<URI> uri(final String name) {
    return new PropertyKey<>(name, PropertyCodec.URI, null);
  }

  public static PropertyKey<Duration> duration(final String name) {
    return new PropertyKey<>(name, PropertyCodec.duration(), null);
  }

  public static PropertyKey<Duration> duration(final String name,
                                               final Duration defaultValue) {
    Objects.requireNonNull(defaultValue, "defaultValue");
    return new PropertyKey<>(name, PropertyCodec.duration(), defaultValue);
  }

  public static <E extends Enum<E>> PropertyKey<E> enumeration(
    final String name,
    final Class<E> type,
    final E defaultValue) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(defaultValue, "defaultValue");
    return new PropertyKey<>(
      name, PropertyCodec.enumeration(type), defaultValue);
  }
}