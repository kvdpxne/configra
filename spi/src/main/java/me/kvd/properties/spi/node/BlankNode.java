package me.kvd.properties.spi.node;

public record BlankNode() implements Node {

  public static final BlankNode INSTANCE = new BlankNode();
}