package me.kvd.configra.spi.node;

import java.util.Objects;

/**
 * A key-value pair commented out with {@link #commentPrefix()}. The
 * {@code line} field follows the same rules as {@link PropertyNode#line()}
 * and is excluded from equality.
 */
public record DisabledPropertyNode(
  String section, String key, String value,
  char commentPrefix, boolean spaceAfterPrefix, int line
) implements Node {

  public DisabledPropertyNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    if (-1 > line) {
      throw new IllegalArgumentException("line must be >= -1");
    }
  }

  /**
   * Convenience: node without source line info.
   */
  public DisabledPropertyNode(final String section, final String key,
                              final String value, final char commentPrefix,
                              final boolean spaceAfterPrefix) {
    this(section, key, value, commentPrefix, spaceAfterPrefix, -1);
  }

  public String qualifiedKey() {
    return null == this.section ? this.key : this.section + '.' + this.key;
  }

  /**
   * Returns the enabled counterpart, preserving the line number.
   */
  public PropertyNode enabled() {
    return new PropertyNode(this.section, this.key, this.value, this.line);
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof DisabledPropertyNode that)) {
      return false;
    }
    return Objects.equals(this.section, that.section)
      && Objects.equals(this.key, that.key)
      && Objects.equals(this.value, that.value)
      && this.commentPrefix == that.commentPrefix
      && this.spaceAfterPrefix == that.spaceAfterPrefix;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.section, this.key, this.value,
      this.commentPrefix, this.spaceAfterPrefix);
  }
}