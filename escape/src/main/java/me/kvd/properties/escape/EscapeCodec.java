package me.kvd.properties.escape;

import java.util.Locale;
import java.util.Objects;
import me.kvd.properties.exceptions.PropertyParseException;

public final class EscapeCodec {

  private EscapeCodec() {
    throw new AssertionError("No instances.");
  }

  // ----- Decoding -----

  public static String unescape(final String value) {
    Objects.requireNonNull(value, "value");
    if (-1 == value.indexOf('\\')) {
      return value;
    }
    final int length = value.length();
    final StringBuilder out = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      final char c = value.charAt(i);
      if ('\\' != c || i + 1 == length) {
        out.append(c);
        continue;
      }
      final char next = value.charAt(++i);
      switch (next) {
        case 't' -> out.append('\t');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 'f' -> out.append('\f');
        case 'u' -> {
          if (i + 4 >= length) {
            throw new PropertyParseException(
              "Truncated \\u escape",
              null, -1, value.substring(Math.max(0, i - 1)));
          }
          int code = 0;
          for (int j = 1; j <= 4; j++) {
            final int digit = Character.digit(
              value.charAt(i + j), 16);
            if (-1 == digit) {
              throw new PropertyParseException(
                "Invalid \\u escape",
                null, -1,
                value.substring(i - 1,
                  Math.min(length, i + 5)));
            }
            code = (code << 4) | digit;
          }
          out.append((char) code);
          i += 4;
        }
        case 'x' -> {
          int code = 0;
          int read = 0;
          while (read < 4 && i + 1 + read < length) {
            final int digit = Character.digit(
              value.charAt(i + 1 + read), 16);
            if (-1 == digit) {
              break;
            }
            code = (code << 4) | digit;
            read++;
          }
          if (0 == read) {
            throw new PropertyParseException(
              "Empty \\x escape",
              null, -1, value.substring(Math.max(0, i - 1)));
          }
          out.append((char) code);
          i += read;
        }
        default -> out.append(next);
      }
    }
    return out.toString();
  }

  // ----- Encoding -----

  /**
   * Escapes a key. Whitespace, {@code =}, {@code :}, {@code #}, {@code !}
   * and any non-ASCII character (when {@code escapeNonAscii} is set) are
   * escaped. Returns the original string when no escaping is needed.
   */
  public static String escapeKey(final String key, final boolean escapeNonAscii) {
    Objects.requireNonNull(key, "key");
    if (!needsEscaping(key, escapeNonAscii, true)) {
      return key;
    }
    final StringBuilder out = new StringBuilder(key.length() + 8);
    for (int i = 0; i < key.length(); i++) {
      final char c = key.charAt(i);
      if (escapeNonAscii && 0x7F < c) {
        appendUnicodeEscape(out, c);
        continue;
      }
      switch (c) {
        case '\\' -> out.append("\\\\");
        case '\t' -> out.append("\\t");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\f' -> out.append("\\f");
        case '=', ':', '#', '!', ' ' -> out.append('\\').append(c);
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Escapes a value. Only the leading space and, when {@code
   * escapeNonAscii} is set, any non-ASCII character are escaped. Returns
   * the original string when no escaping is needed.
   */
  public static String escapeValue(final String value, final boolean escapeNonAscii) {
    Objects.requireNonNull(value, "value");
    if (!needsEscaping(value, escapeNonAscii, false)) {
      return value;
    }
    final StringBuilder out = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (escapeNonAscii && 0x7F < c) {
        appendUnicodeEscape(out, c);
        continue;
      }
      switch (c) {
        case '\\' -> out.append("\\\\");
        case '\t' -> out.append("\\t");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\f' -> out.append("\\f");
        case ' ' -> {
          if (0 == i) {
            out.append("\\ ");
          } else {
            out.append(' ');
          }
        }
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  // ----- Fast-path pre-scan -----

  private static boolean needsEscaping(final String text,
                                       final boolean escapeNonAscii,
                                       final boolean isKey) {
    final int length = text.length();
    for (int i = 0; i < length; i++) {
      final char c = text.charAt(i);
      switch (c) {
        case '\\', '\t', '\n', '\r', '\f' -> {
          return true;
        }
        case ' ' -> {
          if (isKey || 0 == i) {
            return true;
          }
        }
        case '=', ':', '#', '!' -> {
          if (isKey) {
            return true;
          }
        }
        default -> {
          if (escapeNonAscii && 0x7F < c) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static void appendUnicodeEscape(final StringBuilder out,
                                          final char c) {
    out.append("\\u");
    final String hex = Integer.toHexString(c).toUpperCase(Locale.ROOT);
    for (int i = hex.length(); i < 4; i++) {
      out.append('0');
    }
    out.append(hex);
  }
}