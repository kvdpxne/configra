package me.kvd.properties.escape;

import java.util.Objects;

/**
 * String-level helpers shared between formats: unquoting, line-ending
 * normalisation, continuation detection.
 */
public final class TextSupport {

  private TextSupport() {
    throw new AssertionError("No instances.");
  }

  /**
   * Removes a matching pair of enclosing quotes ({@code "..."} or
   * {@code '...'}). No-op when the string is shorter than two characters
   * or not quoted.
   */
  public static String unquote(final String value) {
    Objects.requireNonNull(value, "value");
    if (2 > value.length()) {
      return value;
    }
    final char first = value.charAt(0);
    final char last = value.charAt(value.length() - 1);
    if ((first == '"' || first == '\'') && first == last) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  /**
   * Replaces {@code \r\n} and lone {@code \r} with {@code \n}. The
   * substitution runs on raw text before escaping, so literal {@code \r}
   * escapes in values remain untouched.
   */
  public static String normaliseLineEndings(final String source) {
    Objects.requireNonNull(source, "source");
    if (-1 == source.indexOf('\r')) {
      return source;
    }
    return source.replace("\r\n", "\n").replace('\r', '\n');
  }

  /**
   * Returns {@code true} when {@code text} ends with an unescaped
   * backslash (odd number of trailing backslashes).
   */
  public static boolean endsWithUnescapedBackslash(final CharSequence text) {
    Objects.requireNonNull(text, "text");
    int count = 0;
    for (int i = text.length() - 1; 0 <= i && '\\' == text.charAt(i); i--) {
      count++;
    }
    return 0 != (count & 1);
  }
}