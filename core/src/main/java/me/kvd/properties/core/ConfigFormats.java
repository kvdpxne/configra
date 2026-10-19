package me.kvd.properties.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.kvd.properties.spi.ConfigFormat;
import me.kvd.properties.spi.ConfigFormatProvider;

/**
 * Discovers {@link ConfigFormat} implementations via {@link ServiceLoader}
 * or a manually populated registry.
 *
 * <h2>Manual registration</h2>
 * <p>Once at least one format has been registered through
 * {@link #register(String, ConfigFormat)}, service-loader discovery is
 * bypassed entirely: only manually registered formats participate in
 * {@link #fromFileName(String)} lookups. This gives callers deterministic
 * behaviour in fat JARs where service-file merging may be unreliable or
 * disabled. Call {@link #reset()} to restore discovery.</p>
 */
public final class ConfigFormats {

  private static final Logger LOGGER =
    Logger.getLogger(ConfigFormats.class.getName());
  private static final List<ConfigFormatProvider> PROVIDERS = load();
  private static final ConcurrentHashMap<String, ConfigFormat> REGISTERED =
    new ConcurrentHashMap<>();

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

  // ----- Manual registry -----

  /**
   * Registers a format under {@code name}. Once called, service-loader
   * discovery is disabled for this JVM and only registered formats are
   * considered by {@link #fromFileName(String)}. Idempotent per name.
   */
  public static void register(final String name, final ConfigFormat format) {
    REGISTERED.put(Objects.requireNonNull(name, "name"),
      Objects.requireNonNull(format, "format"));
  }

  /**
   * Removes a specific manually registered format.
   */
  public static void unregister(final String name) {
    REGISTERED.remove(Objects.requireNonNull(name, "name"));
  }

  /**
   * Clears all manual registrations, re-enabling discovery.
   */
  public static void reset() {
    REGISTERED.clear();
  }

  /**
   * Returns {@code true} when at least one manual registration exists.
   */
  public static boolean hasManualRegistrations() {
    return !REGISTERED.isEmpty();
  }

  // ----- Lookup -----

  public static List<ConfigFormatProvider> providers() {
    return PROVIDERS;
  }

  public static Optional<ConfigFormat> fromFileName(final String fileName) {
    Objects.requireNonNull(fileName, "fileName");
    if (!REGISTERED.isEmpty()) {
      final String lower = fileName.toLowerCase(Locale.ROOT);
      for (final ConfigFormat format : REGISTERED.values()) {
        final String ext = format.extension();
        if (!ext.isEmpty() && lower.endsWith(ext)) {
          return Optional.of(format);
        }
      }
      return Optional.empty();
    }
    for (final ConfigFormatProvider provider : PROVIDERS) {
      if (provider.matches(fileName)) {
        return Optional.of(provider.format());
      }
    }
    return Optional.empty();
  }

  public static ConfigFormat requireFromFileName(final String fileName) {
    return fromFileName(fileName).orElseThrow(() -> {
      final StringBuilder sb = new StringBuilder(
        "No ConfigFormat registered for file: ").append(fileName);
      if (!REGISTERED.isEmpty()) {
        sb.append(" (registered: ").append(REGISTERED.keySet()).append(')');
      } else if (!PROVIDERS.isEmpty()) {
        sb.append(" (providers:");
        for (final ConfigFormatProvider provider : PROVIDERS) {
          sb.append(' ').append(provider.extension());
        }
        sb.append(')');
      }
      return new IllegalStateException(sb.toString());
    });
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

  public static boolean isProperties(final ConfigFormat format) {
    Objects.requireNonNull(format, "format");
    return "properties".equals(format.name());
  }
}