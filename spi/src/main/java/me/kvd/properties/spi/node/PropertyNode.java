package me.kvd.properties.spi.node;

import java.util.Objects;

public record PropertyNode(String section, String key, String value) implements Node {

  public PropertyNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
  }

  public String qualifiedKey() {
    return null == this.section ? this.key : this.section + '.' + this.key;
  }
}