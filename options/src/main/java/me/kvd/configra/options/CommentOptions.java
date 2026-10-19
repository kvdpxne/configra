package me.kvd.configra.options;

import java.util.Objects;
import me.kvd.configra.CommentChars;

/**
 * Comment handling: which markers are accepted, and whether they can appear
 * inline, spaced, or represent disabled properties.
 *
 * @param chars         accepted comment markers
 * @param spaceAfter    emit a space after the marker when writing
 * @param inline        accept inline comments ({@code value ; comment})
 * @param parseDisabled recognise commented-out {@code key = value} pairs
 */
public record CommentOptions(
  CommentChars chars,
  boolean spaceAfter,
  boolean inline,
  boolean parseDisabled
) {

  /**
   * Spec default: {@code #} and {@code !}, spaced, no inline, no disabled.
   */
  public static final CommentOptions PROPERTIES =
    new CommentOptions(CommentChars.HASH_AND_BANG, true, false, false);

  /**
   * INI default: {@code ;} and {@code #}, unspaced.
   */
  public static final CommentOptions INI =
    new CommentOptions(CommentChars.SEMICOLON_AND_HASH, false, false, false);

  public CommentOptions {
    Objects.requireNonNull(chars, "chars");
  }
}