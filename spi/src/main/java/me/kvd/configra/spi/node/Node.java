package me.kvd.configra.spi.node;

public sealed interface Node permits BlankNode, CommentNode, SectionNode,
  PropertyNode, DisabledPropertyNode {
}