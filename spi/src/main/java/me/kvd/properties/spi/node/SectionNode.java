package me.kvd.properties.spi.node;

import java.util.Objects;

public record SectionNode(String name) implements Node {

  public SectionNode {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("Section name must not be blank");
    }
  }
}