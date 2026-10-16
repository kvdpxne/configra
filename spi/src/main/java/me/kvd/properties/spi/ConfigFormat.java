package me.kvd.properties.spi;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import me.kvd.properties.options.ParseOptions;
import me.kvd.properties.options.WriteOptions;
import me.kvd.properties.spi.node.Node;

/**
 * Pluggable parser/writer for a config file syntax.
 *
 * <p>Methods that take a {@link ParseOptions} or {@link WriteOptions} have
 * defaults that delegate to the option-less variant, so existing
 * implementations stay source-compatible.</p>
 */
public interface ConfigFormat {

  List<Node> parse(String source);

  default List<Node> parse(final String source, final ParseOptions options) {
    Objects.requireNonNull(options, "options");
    return parse(source);
  }

  /**
   * Scans {@code source} and returns the raw value of the first matching
   * fully qualified key without materialising the whole file.
   */
  Optional<String> peek(Reader source, String qualifiedKey) throws IOException;

  default Optional<String> peek(final Reader source,
                                final String qualifiedKey,
                                final ParseOptions options) throws IOException {
    Objects.requireNonNull(options, "options");
    return peek(source, qualifiedKey);
  }

  /**
   * Reads several keys in a single pass over {@code source}. Returns a map
   * of only the keys that were found, in the order given by {@code keys}
   * (via the first matching occurrence). Streaming is lazy: iteration stops
   * as soon as every requested key has been seen.
   *
   * @param source  source to scan; the caller owns the reader
   * @param keys    requested fully qualified keys; must not be {@code null}
   * @param options parse options for the format
   * @return an unmodifiable map of {@code key → rawValue}
   */
  default Map<String, String> peekAll(final Reader source,
                                      final Collection<String> keys,
                                      final ParseOptions options) throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(options, "options");
    if (keys.isEmpty()) {
      return Map.of();
    }
    final Set<String> wanted = new HashSet<>(keys);
    final Map<String, String> result = new LinkedHashMap<>(wanted.size());
    try (Stream<Map.Entry<String, String>> stream = this.stream(source, options)) {
      final Iterator<Map.Entry<String, String>> it = stream.iterator();
      while (it.hasNext() && result.size() < wanted.size()) {
        final Map.Entry<String, String> entry = it.next();
        if (wanted.contains(entry.getKey())) {
          result.putIfAbsent(entry.getKey(), entry.getValue());
        }
      }
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Lazily streams the file's entries as {@code (qualifiedKey → rawValue)}.
   * The caller owns {@code source}.
   */
  Stream<Map.Entry<String, String>> stream(Reader source);

  default Stream<Map.Entry<String, String>> stream(final Reader source,
                                                   final ParseOptions options) {
    Objects.requireNonNull(options, "options");
    return stream(source);
  }

  String write(List<Node> nodes);

  default String write(final List<Node> nodes, final WriteOptions options) {
    Objects.requireNonNull(options, "options");
    return write(nodes);
  }

  default String name() {
    return this.getClass().getSimpleName();
  }
}