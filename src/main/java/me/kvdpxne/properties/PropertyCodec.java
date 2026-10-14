package me.kvdpxne.properties;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import me.kvdpxne.properties.exceptions.PropertiesException;

/**
 * Bidirectional converter between a raw string and a strongly typed value.
 *
 * <p>Stateless codecs are exposed as constants, so that
 * {@link PropertyKey} factories return shared instances. This makes
 * {@code PropertyCodec} implementations monomorphic at typical call sites,
 * which the JIT can inline.</p>
 *
 * @param <T> the target type
 */
public interface PropertyCodec<T> {

  T decode(String raw) throws PropertiesException;

  String encode(T value) throws PropertiesException;

  // ----- Built-in codecs -----

  PropertyCodec<String> STRING = of(value -> value, value -> value);

  PropertyCodec<Integer> INTEGER = of(
    value -> Integer.parseInt(value.trim()),
    Object::toString);

  PropertyCodec<Long> LONG = of(
    value -> Long.parseLong(value.trim()),
    Object::toString);

  PropertyCodec<Double> DOUBLE = of(
    value -> Double.parseDouble(value.trim()),
    Object::toString);

  PropertyCodec<Boolean> BOOLEAN = of(
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

  PropertyCodec<Path> PATH = of(
    value -> Paths.get(value),
    Path::toString);

  PropertyCodec<URI> URI = of(
    java.net.URI::create,
    java.net.URI::toString);

  /**
   * Comma-separated list of trimmed, non-empty strings. Commas and
   * backslashes inside elements are escaped as {@code \,} and {@code \\}.
   */
  PropertyCodec<List<String>> STRING_LIST = of(
    PropertyCodec::decodeStringList,
    PropertyCodec::encodeStringList);

  /**
   * ISO-8601 ({@code PT30S}) or friendly ({@code 30s}, {@code 5m},
   * {@code 1h30m}, {@code 500ms}) duration. Always encoded as ISO-8601.
   */
  PropertyCodec<Duration> DURATION = of(
    PropertyCodec::parseDuration,
    Duration::toString);

  // ----- Factories (return shared singletons) -----

  static PropertyCodec<List<String>> stringList() {
    return STRING_LIST;
  }

  static PropertyCodec<Duration> duration() {
    return DURATION;
  }

  static <E extends Enum<E>> PropertyCodec<E> enumeration(final Class<E> type) {
    Objects.requireNonNull(type, "type");
    return of(
      raw -> Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT)),
      Enum::name);
  }

  // ----- Generic factory -----

  static <T> PropertyCodec<T> of(final Function<String, T> decoder,
                                 final Function<T, String> encoder) {
    Objects.requireNonNull(decoder, "decoder");
    Objects.requireNonNull(encoder, "encoder");
    return new PropertyCodec<>() {

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
    };
  }

  // ----- Internals -----

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