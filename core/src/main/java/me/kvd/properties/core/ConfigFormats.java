package me.kvd.properties.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.kvd.properties.spi.ConfigFormat;
import me.kvd.properties.spi.ConfigFormatProvider;

/**
 * Discovers {@link ConfigFormat} implementations via {@link ServiceLoader}.
 * Errors during discovery are isolated: a broken provider is skipped with a
 * warning so the rest of the classpath remains usable.
 */
public final class ConfigFormats {

  private static final Logger LOGGER = Logger.getLogger(ConfigFormats.class.getName());
  private static final List<ConfigFormatProvider> PROVIDERS = load();

  private ConfigFormats() {
    throw new AssertionError("No instances.");
  }

  private static List<ConfigFormatProvider> load() {
    final List<ConfigFormatProvider> result = new ArrayList<>();
    for (final ConfigFormatProvider provider
      : ServiceLoader.load(ConfigFormatProvider.class)) {
      try {
        Objects.requireNonNull(provider.format(),
          "provider.format() returned null for " + provider);
        result.add(provider);
      } catch (final LinkageError | RuntimeException failure) {
        LOGGER.log(Level.WARNING,
          "Skipping config format provider " + provider
            + " due to load failure", failure);
      }
    }
    result.sort(Comparator.comparingInt(ConfigFormatProvider::priority));
    return List.copyOf(result);
  }

  public static List<ConfigFormatProvider> providers() {
    return PROVIDERS;
  }

  public static Optional<ConfigFormat> fromFileName(final String fileName) {
    Objects.requireNonNull(fileName, "fileName");
    for (final ConfigFormatProvider provider : PROVIDERS) {
      if (provider.matches(fileName)) {
        return Optional.of(provider.format());
      }
    }
    return Optional.empty();
  }

  public static ConfigFormat requireFromFileName(final String fileName) {
    return fromFileName(fileName).orElseThrow(() -> new IllegalStateException(
      "No ConfigFormat registered for file: " + fileName));
  }

  /**
   * Returns {@code true} when {@code format} is an INI dialect. Uses the
   * stable {@link ConfigFormat#name()} identifier rather than a
   * class-name reflection check.
   */
  public static boolean isIni(final ConfigFormat format) {
    Objects.requireNonNull(format, "format");
    return "ini".equals(format.name());
  }
}