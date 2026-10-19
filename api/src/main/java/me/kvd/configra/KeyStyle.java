package me.kvd.configra;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Naming convention applied to config keys when reading or writing a file.
 *
 * <p>Canonical form (used by {@link PropertyKey#name()}) is
 * {@link #CAMEL_CASE}. Reading a file with {@link ParseOptions#keyStyle}
 * different from {@link #NONE} converts raw file keys to canonical form;
 * writing with {@link WriteOptions#keyStyle} converts them back. This lets
 * the same Java code work with files that use any of the popular styles.</p>
 *
 * <p>Dot ({@code '.'}) is always preserved as a section separator and is
 * never part of a word. For INI, this means {@code [time-zone].mode}
 * canonicalizes to {@code timeZone.mode}.</p>
 */
public enum KeyStyle {

  /**
   * No conversion. Keys are used verbatim.
   */
  NONE,

  /**
   * {@code daySyncMode}. Default canonical form.
   */
  CAMEL_CASE,

  /**
   * {@code DaySyncMode}.
   */
  PASCAL_CASE,

  /**
   * {@code day_sync_mode}.
   */
  SNAKE_CASE,

  /**
   * {@code day-sync-mode}.
   */
  KEBAB_CASE,

  /**
   * {@code DAY_SYNC_MODE}. Useful for environment-variable bridges.
   */
  SCREAMING_SNAKE;

  // ----- Public API -----

  /**
   * Converts {@code key} from this style to the canonical style
   * ({@link #CAMEL_CASE}). Returns {@code key} unchanged when this style
   * is {@link #NONE} or already canonical.
   */
  public String toCanonical(final String key) {
    return convert(key, this, CAMEL_CASE);
  }

  /**
   * Converts a canonical camelCase {@code key} to this style. Returns
   * {@code key} unchanged when this style is {@link #NONE} or already
   * canonical.
   */
  public String fromCanonical(final String key) {
    return convert(key, CAMEL_CASE, this);
  }

  // ----- Internals -----

  private static String convert(final String key,
                                final KeyStyle source,
                                final KeyStyle target) {
    Objects.requireNonNull(key, "key");
    if (NONE == source || NONE == target || source == target) {
      return key;
    }
    // Fast path: no separators and no case transition means a single
    // lowercase word; identity under any style.
    if (isSingleLowercaseWord(key)) {
      return key;
    }
    final StringBuilder out = new StringBuilder(key.length() + 8);
    int start = 0;
    final int length = key.length();
    for (int i = 0; i <= length; i++) {
      if (i == length || '.' == key.charAt(i)) {
        if (i > start) {
          out.append(joinWords(splitWords(key.substring(start, i)),
            target));
        }
        if (i < length) {
          out.append('.');
        }
        start = i + 1;
      }
    }
    return out.toString();
  }

  private static boolean isSingleLowercaseWord(final String s) {
    if (s.isEmpty()) {
      return true;
    }
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if ('.' == c || '_' == c || '-' == c
        || Character.isUpperCase(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Splits {@code segment} into lowercase-canonical words. Handles mixed
   * input: {@code day-sync-mode}, {@code day_sync_mode}, {@code daySyncMode},
   * {@code DaySyncMode}, {@code DAY_SYNC_MODE}, {@code HTMLParser}.
   */
  private static List<String> splitWords(final String segment) {
    final List<String> words = new ArrayList<>(4);
    final int n = segment.length();
    int start = 0;
    for (int i = 0; i < n; i++) {
      final char c = segment.charAt(i);
      if ('_' == c || '-' == c) {
        if (i > start) {
          words.add(segment.substring(start, i).toLowerCase(Locale.ROOT));
        }
        start = i + 1;
        continue;
      }
      // Lowercase/digit → uppercase boundary: "daySync" → "day|Sync"
      if (i > start
        && Character.isUpperCase(c)
        && (Character.isLowerCase(segment.charAt(i - 1))
        || Character.isDigit(segment.charAt(i - 1)))) {
        words.add(segment.substring(start, i).toLowerCase(Locale.ROOT));
        start = i;
        continue;
      }
      // Acronym boundary: "HTMLParser" → "HTML|Parser"
      if (i > start
        && Character.isUpperCase(c)
        && i + 1 < n
        && Character.isLowerCase(segment.charAt(i + 1))
        && Character.isUpperCase(segment.charAt(i - 1))) {
        words.add(segment.substring(start, i).toLowerCase(Locale.ROOT));
        start = i;
        continue;
      }
    }
    if (start < n) {
      words.add(segment.substring(start).toLowerCase(Locale.ROOT));
    }
    return words;
  }

  private static String joinWords(final List<String> words,
                                  final KeyStyle target) {
    if (words.isEmpty()) {
      return "";
    }
    return switch (target) {
      case NONE -> String.join("", words);
      case CAMEL_CASE -> {
        final StringBuilder sb = new StringBuilder(words.size() * 8);
        for (int i = 0; i < words.size(); i++) {
          final String w = words.get(i);
          if (0 == i || Character.isDigit(w.charAt(0))) {
            sb.append(w);
          } else {
            sb.append(capitalize(w));
          }
        }
        yield sb.toString();
      }
      case PASCAL_CASE -> {
        final StringBuilder sb = new StringBuilder(words.size() * 8);
        for (final String w : words) {
          sb.append(capitalize(w));
        }
        yield sb.toString();
      }
      case SNAKE_CASE -> String.join("_", words);
      case KEBAB_CASE -> String.join("-", words);
      case SCREAMING_SNAKE -> String.join("_", upper(words));
    };
  }

  private static List<String> upper(final List<String> words) {
    final List<String> out = new ArrayList<>(words.size());
    for (final String w : words) {
      out.add(w.toUpperCase(Locale.ROOT));
    }
    return out;
  }

  private static String capitalize(final String word) {
    if (word.isEmpty()) {
      return word;
    }
    final char first = Character.toUpperCase(word.charAt(0));
    return first + word.substring(1);
  }
}