package me.kvd.properties.exceptions;

import java.io.IOException;
import java.io.Serial;

/**
 * Raised when persisting a configuration fails for a reason other than
 * I/O (which is exposed as {@link IOException}). Wraps the underlying
 * serialisation failure with the target path.
 */
public final class PropertyWriteException extends PropertiesException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String target;

  public PropertyWriteException(final String target,
                                final String message,
                                final Throwable cause) {
    super(message, cause);
    this.target = target;
  }

  public String target() {
    return this.target;
  }
}