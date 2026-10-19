package me.kvd.configra;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only view over a source of raw string properties.
 */
@FunctionalInterface
public interface PropertySource {

  Optional<String> find(String name);

  static PropertySource ofSystem() {
    return name -> {
      Objects.requireNonNull(name, "name");
      return Optional.ofNullable(System.getProperty(name));
    };
  }

  /**
   * Environment variables with a prefix. Name transformation
   * ({@code name → PREFIX_NAME_UPPER}) is cached per distinct key.
   */
  static PropertySource ofEnvironment(final String prefix) {
    Objects.requireNonNull(prefix, "prefix");
    final ConcurrentHashMap<String, String> nameCache =
      new ConcurrentHashMap<>(16, 0.75f, 1);
    return name -> {
      Objects.requireNonNull(name, "name");
      final String envName = nameCache.computeIfAbsent(name,
        n -> prefix + n.toUpperCase(Locale.ROOT).replace('.', '_'));
      return Optional.ofNullable(System.getenv(envName));
    };
  }

  static PropertySource chain(final PropertySource... sources) {
    Objects.requireNonNull(sources, "sources");
    final PropertySource[] copy = sources.clone();
    for (final PropertySource source : copy) {
      Objects.requireNonNull(source, "source");
    }
    return name -> {
      Objects.requireNonNull(name, "name");
      for (final PropertySource source : copy) {
        final Optional<String> value = source.find(name);
        if (value.isPresent()) {
          return value;
        }
      }
      return Optional.empty();
    };
  }
}