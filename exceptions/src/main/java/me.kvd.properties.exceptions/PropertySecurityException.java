package me.kvd.properties.exceptions;

import java.io.Serial;

/**
 * Raised when an operation is refused for security reasons: path traversal,
 * insufficient filesystem permissions, or a target outside the configured
 * sandbox.
 */
public final class PropertySecurityException extends PropertiesException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String path;

  public PropertySecurityException(final String path,
                                   final String message) {
    super(message);
    this.path = path;
  }

  public String path() {
    return this.path;
  }
}