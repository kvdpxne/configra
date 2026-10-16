package me.kvd.properties;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import me.kvd.properties.exceptions.PropertiesException;

/**
 * Bidirectional converter between a raw string and a strongly typed value.
 *
 * <p>Stateless codecs are exposed as constants. {@link #enumeration(Class)}
 * caches its result per {@code Class}, so repeated calls with the same enum
 * type return the same instance — which keeps {@link PropertyKey} equality
 * (and therefore the per-instance decode cache in
 * {@link ConfigFile}) working as intended.</p>
 *
 * @param <T> the target type
 */
public interface PropertyCodec<T> {

  T decode(String raw) throws PropertiesException;

  String encode(T value) throws PropertiesException;

  /**
   * Returns the runtime type this codec produces. Used by batch mutation
   * operations to reject mismatched values at insertion time. Defaults to
   * {@link Object#getClass() Object.class}, which disables the check —
   * codecs built via {@link #of(Function, Function)} keep this default.
   */
  default Class<?> valueType() {
    return Object.class;
  }

  // ----- Built-in codecs -----

  PropertyCodec<String> STRING = of(String.class, value -> value, value -> value);

  PropertyCodec<Integer> INTEGER = of(Integer.class,
    value -> Integer.parseInt(value.trim()),
    Object::toString);

  PropertyCodec<Long> LONG = of(Long.class,
    value -> Long.parseLong(value.trim()),
    Object::toString);

  PropertyCodec<Double> DOUBLE = of(Double.class,
    value -> Double.parseDouble(value.trim()),
    Object::toString);

  PropertyCodec<Boolean> BOOLEAN = of(Boolean.class,
    value -> {
      final String normalised = value.trim().toLowerCase(Locale.ROOT);
      return switch (normalised) {
        case "true", "yes", "on", "1" -> Boolean.TRUE;
        case "false", "no", "off", "0" -> Boolean.FALSE;
        default -> throw new PropertiesException(
          "Not a boolean: " + value);
      };
    },
    Object::toString);

  PropertyCodec<Path> PATH = of(Path.class,
    Paths::get,
    Path::toString);

  PropertyCodec<URI> URI = of(URI.class,
    java.net.URI::create,
    java.net.URI::toString);

  PropertyCodec<List<String>> STRING_LIST = of(List.class,
    PropertyCodec::decodeStringList,
    PropertyCodec::encodeStringList);

  PropertyCodec<Duration> DURATION = of(Duration.class,
    PropertyCodec::parseDuration,
    Duration::toString);

  // ----- Factories -----

  static PropertyCodec<List<String>> stringList() {
    return STRING_LIST;
  }

  static PropertyCodec<Duration> duration() {
    return DURATION;
  }

  /**
   * Enum codec for {@code type}. The instance is cached in a
   * {@link ClassValue}, so two keys built from the same enum class share
   * the same codec instance — required for reliable {@code equals} on
   * {@link PropertyKey} and for the decode cache.
   */
  @SuppressWarnings("unchecked")
  static <E extends Enum<E>> PropertyCodec<E> enumeration(final Class<E> type) {
    Objects.requireNonNull(type, "type");
    return (PropertyCodec<E>) ENUM_CODECS.get(type);
  }

  /**
   * Typed codec factory. The {@code type} argument is used to answer
   * {@link #valueType()} so that batch operations can validate values at
   * insertion time.
   *
   * <p>The parameter is {@code Class<?>} rather than {@code Class<T>} to
   * allow raw class literals such as {@code List.class} or
   * {@code Object.class} to be passed without casts. The value type
   * {@code T} is inferred from the decoder and encoder functions.</p>
   */
  static <T> PropertyCodec<T> of(final Class<?> type,
                                 final Function<String, T> decoder,
                                 final Function<T, String> encoder) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(decoder, "decoder");
    Objects.requireNonNull(encoder, "encoder");
    return new PropertyCodec<T>() {
      @Override
      public T decode(final String raw) throws PropertiesException {
        Objects.requireNonNull(raw, "raw");
        try {
          return decoder.apply(raw);
        } catch (final PropertiesException exception) {
          throw exception;
        } catch (final RuntimeException exception) {
          throw new PropertiesException(
            "Cannot decode value '" + raw + '\'', exception);
        }
      }

      @Override
      public String encode(final T value) throws PropertiesException {
        Objects.requireNonNull(value, "value");
        try {
          return encoder.apply(value);
        } catch (final PropertiesException exception) {
          throw exception;
        } catch (final RuntimeException exception) {
          throw new PropertiesException(
            "Cannot encode value", exception);
        }
      }

      @Override
      public Class<?> valueType() {
        return type;
      }
    };
  }

  /**
   * Untyped codec factory. {@link #valueType()} falls back to
   * {@code Object.class}, so no type check runs during batch operations.
   */
  static <T> PropertyCodec<T> of(final Function<String, T> decoder,
                                 final Function<T, String> encoder) {
    return of(Object.class, decoder, encoder);
  }

  // ----- Enum codec cache -----

  /**
   * Per-{@code Class} cache of enum codecs. {@link ClassValue} is
   * GC-friendly: entries are dropped automatically when the corresponding
   * class is unloaded, and the lookup is a lock-free read on the hot path.
   */
  ClassValue<PropertyCodec<?>> ENUM_CODECS = new ClassValue<>() {
    @Override
    protected PropertyCodec<?> computeValue(final Class<?> type) {
      return createEnumCodec(type);
    }
  };

  @SuppressWarnings({"unchecked", "rawtypes"})
  static PropertyCodec<?> createEnumCodec(final Class<?> type) {
    return new PropertyCodec() {

      @Override
      public Object decode(final String raw) throws PropertiesException {
        Objects.requireNonNull(raw, "raw");
        try {
          return Enum.valueOf((Class<? extends Enum>) type,
            raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
          throw new PropertiesException(
            "Not a valid " + type.getSimpleName()
              + ": " + raw, exception);
        }
      }

      @Override
      public String encode(final Object value) throws PropertiesException {
        Objects.requireNonNull(value, "value");
        return ((Enum) value).name();
      }

      @Override
      public Class<?> valueType() {
        return type;
      }
    };
  }

  // ----- Internal helpers -----

  private static List<String> decodeStringList(final String raw) {
    if (raw.isBlank()) {
      return List.of();
    }
    final List<String> result = new ArrayList<>();
    final StringBuilder current = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      final char c = raw.charAt(i);
      if ('\\' == c && i + 1 < raw.length()) {
        final char next = raw.charAt(i + 1);
        if (',' == next || '\\' == next) {
          current.append(next);
          i++;
          continue;
        }
      }
      if (',' == c) {
        final String trimmed = current.toString().trim();
        if (!trimmed.isEmpty()) {
          result.add(trimmed);
        }
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    final String trimmed = current.toString().trim();
    if (!trimmed.isEmpty()) {
      result.add(trimmed);
    }
    return List.copyOf(result);
  }

  private static String encodeStringList(final List<String> list) {
    if (list.isEmpty()) {
      return "";
    }
    final StringBuilder out = new StringBuilder(list.size() * 16);
    for (int i = 0; i < list.size(); i++) {
      if (0 < i) {
        out.append(", ");
      }
      final String element = list.get(i);
      for (int j = 0; j < element.length(); j++) {
        final char c = element.charAt(j);
        if (',' == c || '\\' == c) {
          out.append('\\');
        }
        out.append(c);
      }
    }
    return out.toString();
  }

  private static Duration parseDuration(final String raw) {
    final String text = raw.trim();
    if (text.isEmpty()) {
      throw new PropertiesException("Empty duration");
    }
    if (text.startsWith("P") || text.startsWith("p")) {
      return Duration.parse(text.toUpperCase(Locale.ROOT));
    }
    long millis = 0L;
    int i = 0;
    while (i < text.length()) {
      final int start = i;
      while (i < text.length() && Character.isDigit(text.charAt(i))) {
        i++;
      }
      if (i == start) {
        throw new PropertiesException("Invalid duration: " + raw);
      }
      final long value = Long.parseLong(text.substring(start, i));
      final String unit;
      if (text.startsWith("ms", i)) {
        unit = "ms";
        i += 2;
      } else if (i < text.length()) {
        unit = String.valueOf(text.charAt(i));
        i++;
      } else {
        throw new PropertiesException(
          "Missing unit in duration: " + raw);
      }
      millis += switch (unit) {
        case "ms" -> value;
        case "s" -> value * 1000L;
        case "m" -> value * 60_000L;
        case "h" -> value * 3_600_000L;
        case "d" -> value * 86_400_000L;
        default -> throw new PropertiesException(
          "Unknown duration unit '" + unit + "' in: " + raw);
      };
    }
    return Duration.ofMillis(millis);
  }
}