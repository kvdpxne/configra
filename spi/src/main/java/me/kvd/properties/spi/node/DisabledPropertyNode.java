package me.kvd.properties.spi.node;

import java.util.Objects;

/**
 * A key-value pair that is present in the file but commented out. Retains
 * the marker and optional space so round-tripping (enable → disable → enable)
 * preserves the original layout.
 */
public record DisabledPropertyNode(
  String section,
  String key,
  String value,
  char commentPrefix,
  boolean spaceAfterPrefix
) implements Node {

  public DisabledPropertyNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
  }

  public String qualifiedKey() {
    return null == this.section ? this.key : this.section + '.' + this.key;
  }

  /**
   * Returns the enabled counterpart of this node.
   */
  public PropertyNode enabled() {
    return new PropertyNode(this.section, this.key, this.value);
  }
}