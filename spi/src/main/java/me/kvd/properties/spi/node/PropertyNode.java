package me.kvd.properties.spi.node;

import java.util.Objects;

/**
 * An active key-value pair. The {@code line} field carries the 0-based
 * source line number when the node came from a parsed file, or {@code -1}
 * when the node was produced programmatically. Line is <b>excluded from
 * equality</b>: two nodes with the same section, key and value are equal
 * regardless of where they appear in their source file.
 */
public record PropertyNode(String section, String key, String value, int line)
  implements Node {

  public PropertyNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    if (-1 > line) {
      throw new IllegalArgumentException("line must be >= -1");
    }
  }

  /**
   * Convenience: node without source line info.
   */
  public PropertyNode(final String section, final String key,
                      final String value) {
    this(section, key, value, -1);
  }

  public String qualifiedKey() {
    return null == this.section ? this.key : this.section + '.' + this.key;
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof PropertyNode that)) {
      return false;
    }
    return Objects.equals(this.section, that.section)
      && Objects.equals(this.key, that.key)
      && Objects.equals(this.value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.section, this.key, this.value);
  }
}