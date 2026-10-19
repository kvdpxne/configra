package me.kvd.configra.exceptions;

import java.io.Serial;

/**
 * Raised when a required key is absent from the configuration and has no
 * default. Never thrown for optional reads ({@code find}, {@code getOrElse}).
 */
public final class PropertyMissingException extends PropertiesException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String key;

  public PropertyMissingException(final String key) {
    super("Missing required property: " + key);
    this.key = key;
  }

  public PropertyMissingException(final String key, final String message) {
    super(message);
    this.key = key;
  }

  public String key() {
    return this.key;
  }
}