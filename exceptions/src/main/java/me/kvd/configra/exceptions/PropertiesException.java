package me.kvd.configra.exceptions;

import java.io.Serial;

/**
 * Root of the exception hierarchy raised by the library.
 *
 * <p><b>Extensibility.</b> The class is {@code sealed} so that pattern
 * matching over permitted subtypes is exhaustive. Third-party code cannot
 * declare new subtypes. If your integration needs a custom exception,
 * declare one outside this hierarchy and wrap it, or attach it as the
 * {@code cause} of one of the existing subtypes.</p>
 */
public sealed class PropertiesException extends RuntimeException
  permits PropertyParseException,
  PropertyMissingException,
  PropertyTypeException,
  PropertyInterpolationException,
  PropertyValidationException,
  PropertyWriteException,
  PropertySecurityException,
  PropertyConcurrencyException {

  @Serial
  private static final long serialVersionUID = 1L;

  public PropertiesException(final String message) {
    super(message);
  }

  public PropertiesException(final String message, final Throwable cause) {
    super(message, cause);
  }
}