package me.kvd.configra;

/**
 * Up to two comment markers accepted or emitted by a format. {@code '\0'}
 * in {@link #secondary()} means "no second marker".
 *
 * <p>Fast: {@link #matches(char)} is two primitive comparisons, no
 * allocation, no boxing. Suitable for a per-character hot path.</p>
 */
public record CommentChars(char primary, char secondary) {

  public static final CommentChars HASH = new CommentChars('#', '\0');
  public static final CommentChars BANG = new CommentChars('!', '\0');
  public static final CommentChars HASH_AND_BANG = new CommentChars('#', '!');
  public static final CommentChars BANG_AND_HASH = new CommentChars('!', '#');
  public static final CommentChars SEMICOLON = new CommentChars(';', '\0');
  public static final CommentChars SEMICOLON_AND_HASH = new CommentChars(';', '#');
  public static final CommentChars HASH_AND_SEMICOLON = new CommentChars('#', ';');

  public CommentChars {
    if ('\0' == primary) {
      throw new IllegalArgumentException("primary must not be NUL");
    }
    if ('\0' != secondary && secondary == primary) {
      throw new IllegalArgumentException(
        "secondary must differ from primary");
    }
  }

  public boolean matches(final char c) {
    return c == this.primary
      || ('\0' != this.secondary && c == this.secondary);
  }

  /**
   * Returns {@code true} if this instance accepts two different markers.
   */
  public boolean isDual() {
    return '\0' != this.secondary;
  }

  /**
   * Returns {@code true} if {@code c} is accepted at all.
   */
  public boolean accepts(final char c) {
    return this.matches(c);
  }

  /**
   * True when {@code c} is emitted by {@link #primary()}.
   */
  public boolean isPrimary(final char c) {
    return c == this.primary;
  }
}