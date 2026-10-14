package me.kvdpxne.properties.exceptions;

/**
 * Raised when a {@code ${...}} reference cannot be resolved: missing target,
 * circular dependency, or malformed marker.
 */
public final class PropertyInterpolationException extends PropertiesException {

  private static final long serialVersionUID = 1L;

  private final String fromKey;
  private final String referencedKey;

  public PropertyInterpolationException(final String fromKey,
                                        final String referencedKey,
                                        final String message) {
    super(message);
    this.fromKey = fromKey;
    this.referencedKey = referencedKey;
  }

  public String fromKey() {
    return this.fromKey;
  }

  public String referencedKey() {
    return this.referencedKey;
  }
}