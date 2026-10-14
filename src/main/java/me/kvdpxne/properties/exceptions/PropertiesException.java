package me.kvdpxne.properties.exceptions;

/**
 * Root of the exception hierarchy raised by the library.
 *
 * <p>Sealed so that callers can pattern-match exhaustively over the
 * permitted subtypes. Every subtype carries enough context to identify the
 * failing key, line or operation without inspecting the stack trace.</p>
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

  private static final long serialVersionUID = 1L;

  public PropertiesException(final String message) {
    super(message);
  }

  public PropertiesException(final String message, final Throwable cause) {
    super(message, cause);
  }
}