package me.kvd.configra.options;

/**
 * Whitespace handling during parsing.
 *
 * @param trimTrailing    strip trailing whitespace from values
 * @param strictDelimiter treat only {@code ' '}, {@code '\t'} and
 *                        {@code '\f'} as delimiter whitespace
 */
public record WhitespaceOptions(boolean trimTrailing, boolean strictDelimiter) {

  /**
   * Spec-literal: preserve trailing, strict delimiters.
   */
  public static final WhitespaceOptions DEFAULT = new WhitespaceOptions(false, true);

  /**
   * Java-compatible: trim trailing, strict delimiters.
   */
  public static final WhitespaceOptions TRIM = new WhitespaceOptions(true, true);

  /**
   * Lenient: preserve trailing, accept any Unicode whitespace as delimiter.
   */
  public static final WhitespaceOptions LENIENT = new WhitespaceOptions(false, false);
}