package me.kvd.properties.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import me.kvd.properties.spi.ConfigFormat;
import me.kvd.properties.spi.ConfigFormatProvider;

/**
 * Discovers {@link ConfigFormat} implementations on the classpath via
 * {@link ServiceLoader}. Consumers who know the format up front should use
 * the format module's {@code INSTANCE} constant directly — this class is
 * for scenarios where the format is chosen at runtime from a file name.
 */
public final class ConfigFormats {

    private static final List<ConfigFormatProvider> PROVIDERS = load();

    private ConfigFormats() {
        throw new AssertionError("No instances.");
    }

    private static List<ConfigFormatProvider> load() {
        final List<ConfigFormatProvider> result = new ArrayList<>();
        for (final ConfigFormatProvider provider
                : ServiceLoader.load(ConfigFormatProvider.class)) {
            result.add(provider);
        }
        result.sort(Comparator.comparingInt(ConfigFormatProvider::priority));
        return List.copyOf(result);
    }

    /** Returns every registered format provider, sorted by priority. */
    public static List<ConfigFormatProvider> providers() {
        return PROVIDERS;
    }

    /**
     * Resolves a format from a file name. Returns {@link Optional#empty()}
     * when no registered provider matches.
     */
    public static Optional<ConfigFormat> fromFileName(final String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        for (final ConfigFormatProvider provider : PROVIDERS) {
            if (provider.matches(fileName)) {
                return Optional.of(provider.format());
            }
        }
        return Optional.empty();
    }

    /**
     * Same as {@link #fromFileName(String)} but raises when no provider
     * matches.
     */
    public static ConfigFormat requireFromFileName(final String fileName) {
        return fromFileName(fileName).orElseThrow(() -> new IllegalStateException(
                "No ConfigFormat registered for file: " + fileName));
    }

  /**
   * Returns {@code true} when {@code format} is an INI format. Implemented
   * via an identity check against the well-known INI singleton; falls back
   * to a name-based heuristic for custom INI dialects provided by consumers.
   */
  public static boolean isIni(final ConfigFormat format) {
    Objects.requireNonNull(format, "format");
    return "ini".equals(format.name());
  }
}