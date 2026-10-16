package me.kvd.properties.spi.node;

public sealed interface Node permits BlankNode, CommentNode, SectionNode,
  PropertyNode, DisabledPropertyNode {
}