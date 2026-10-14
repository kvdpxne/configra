package me.kvdpxne.properties;

import java.util.Objects;

sealed interface Node permits BlankNode, CommentNode, SectionNode,
  PropertyNode, DisabledPropertyNode {
}

record BlankNode() implements Node {
  static final BlankNode INSTANCE = new BlankNode();
}

record CommentNode(String text) implements Node {
  CommentNode {
    Objects.requireNonNull(text, "text");
  }
}

record SectionNode(String name) implements Node {
  SectionNode {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("Section name must not be blank");
    }
  }
}

record PropertyNode(String section, String key, String value) implements Node {
  PropertyNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
  }

  String qualifiedKey() {
    return null == this.section ? this.key : this.section + '.' + this.key;
  }
}

/**
 * A key-value pair that is present in the file but commented out. Retains
 * the marker and optional space so round-tripping (enable → disable → enable)
 * preserves the original layout.
 */
record DisabledPropertyNode(
  String section,
  String key,
  String value,
  char commentPrefix,
  boolean spaceAfterPrefix
) implements Node {

  DisabledPropertyNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
  }

  String qualifiedKey() {
    return null == this.section ? this.key : this.section + '.' + this.key;
  }

  /**
   * Returns the enabled counterpart of this node.
   */
  PropertyNode enabled() {
    return new PropertyNode(this.section, this.key, this.value);
  }
}