package me.kvd.properties.spi;

import java.util.Locale;

/**
 * ServiceLoader entry point for {@link ConfigFormat} implementations.
 * Each format module ships a {@code META-INF/services} descriptor listing
 * its provider, so {@code ConfigFormats.fromFileName(...)} can locate
 * formats without compile-time coupling.
 */
public interface ConfigFormatProvider {

  /**
   * Returns a fresh or shared instance of the format.
   */
  ConfigFormat format();

  /**
   * Returns {@code true} when this provider handles files with the given
   * name (usually dispatched on file extension).
   */
  default boolean matches(final String fileName) {
    return fileName.toLowerCase(Locale.ROOT)
      .endsWith(this.extension());
  }

  /**
   * Lower-case extension, including the dot, e.g. {@code ".ini"}.
   */
  String extension();

  /**
   * Lower values win.
   */
  default int priority() {
    return 100;
  }
}