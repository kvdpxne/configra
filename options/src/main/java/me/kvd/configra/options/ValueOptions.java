package me.kvd.configra.options;

/**
 * Value-level parsing toggles that affect a single value at a time.
 *
 * @param unquote              strip a matching pair of enclosing quotes
 * @param relativeNesting      expand {@code [.subsection]} under the parent
 * @param indentedContinuation accept Python {@code configparser}-style
 *                             multi-line values via indentation
 */
public record ValueOptions(
  boolean unquote,
  boolean relativeNesting,
  boolean indentedContinuation
) {

  public static final ValueOptions DEFAULT =
    new ValueOptions(false, false, false);
}