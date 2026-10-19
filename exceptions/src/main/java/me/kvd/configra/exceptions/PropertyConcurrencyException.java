package me.kvd.configra.exceptions;

import java.io.Serial;

/**
 * Raised when a configuration instance is accessed from a thread that was
 * not expected by the caller's concurrency contract — for example, when an
 * async callback is configured with {@code strictThread = true} and is
 * invoked from a thread other than the one that opened the file.
 */
public final class PropertyConcurrencyException extends PropertiesException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String expectedThread;
  private final String actualThread;

  public PropertyConcurrencyException(final String expectedThread,
                                      final String actualThread,
                                      final String message) {
    super(message);
    this.expectedThread = expectedThread;
    this.actualThread = actualThread;
  }

  public String expectedThread() {
    return this.expectedThread;
  }

  public String actualThread() {
    return this.actualThread;
  }
}