package me.kvdpxne.properties.exceptions;

import java.util.Objects;

/**
 * Raised when a source file cannot be parsed. Carries the zero-based line
 * index and the offending snippet so a caller can point the user at the
 * problem without re-reading the file.
 */
public final class PropertyParseException extends PropertiesException {

  private static final long serialVersionUID = 1L;

  private final String sourceName;
  private final int line;
  private final String snippet;

  public PropertyParseException(final String message,
                                final String sourceName,
                                final int line,
                                final String snippet) {
    super(buildMessage(message, sourceName, line, snippet));
    this.sourceName = sourceName;
    this.line = line;
    this.snippet = Objects.requireNonNull(snippet, "snippet");
  }

  public PropertyParseException(final String message,
                                final String sourceName,
                                final int line,
                                final String snippet,
                                final Throwable cause) {
    super(buildMessage(message, sourceName, line, snippet), cause);
    this.sourceName = sourceName;
    this.line = line;
    this.snippet = Objects.requireNonNull(snippet, "snippet");
  }

  public String sourceName() {
    return this.sourceName;
  }

  public int line() {
    return this.line;
  }

  public String snippet() {
    return this.snippet;
  }

  private static String buildMessage(final String message,
                                     final String sourceName,
                                     final int line,
                                     final String snippet) {
    final StringBuilder out = new StringBuilder(message.length() + 64);
    out.append(message);
    if (null != sourceName) {
      out.append(" in ").append(sourceName);
    }
    if (0 <= line) {
      out.append(" at line ").append(line + 1);
    }
    if (!snippet.isEmpty()) {
      out.append(": ").append(snippet);
    }
    return out.toString();
  }
}