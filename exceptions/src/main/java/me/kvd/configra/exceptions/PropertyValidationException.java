package me.kvd.configra.exceptions;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/**
 * Raised by {@link me.kvdpxne.properties.PropertySchema#validate} when one
 * or more required keys are absent. The full list is exposed so the caller
 * can report every problem at once instead of failing on the first.
 */
public final class PropertyValidationException extends PropertiesException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final List<String> missingKeys;

  public PropertyValidationException(final List<String> missingKeys) {
    super("Missing required properties: " + missingKeys);
    this.missingKeys = List.copyOf(
      Objects.requireNonNull(missingKeys, "missingKeys"));
  }

  public List<String> missingKeys() {
    return this.missingKeys;
  }
}