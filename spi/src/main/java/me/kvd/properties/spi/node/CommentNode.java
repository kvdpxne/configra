package me.kvd.properties.spi.node;

import java.util.Objects;

public record CommentNode(String text) implements Node {

  public CommentNode {
    Objects.requireNonNull(text, "text");
  }
}