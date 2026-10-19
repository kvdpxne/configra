package me.kvd.configra.exceptions;

import java.io.Serial;

/**
 * Raised when a codec rejects a raw value or fails to encode a typed value.
 * Carries the key and the offending raw form, when available.
 */
public final class PropertyTypeException extends PropertiesException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String key;
  private final String rawValue;

  public PropertyTypeException(final String key,
                               final String rawValue,
                               final String message,
                               final Throwable cause) {
    super(message, cause);
    this.key = key;
    this.rawValue = rawValue;
  }

  public PropertyTypeException(final String message,
                               final Throwable cause) {
    super(message, cause);
    this.key = null;
    this.rawValue = null;
  }

  public String key() {
    return this.key;
  }

  public String rawValue() {
    return this.rawValue;
  }
}