package me.kvd.configra.spi.node;

public record BlankNode() implements Node {

  public static final BlankNode INSTANCE = new BlankNode();
}