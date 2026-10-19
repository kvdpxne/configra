package me.kvd.configra.core;

import java.time.Duration;
import java.util.List;
import me.kvd.configra.PropertyKey;
import me.kvd.configra.format.ini.IniFormat;
import me.kvd.configra.format.properties.PropertiesFormat;
import me.kvd.configra.spi.node.CommentNode;
import me.kvd.configra.spi.node.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBuilderTest {

  private static List<Node> nodesOf(final ConfigFile file) {
    return file.nodes();
  }

  @Test
  void simpleKeyValue() {
    final ConfigFile config = new ConfigBuilder()
      .setString("name", "value")
      .build(PropertiesFormat.INSTANCE);
    assertEquals("value", config.require(PropertyKey.string("name")));
  }

  @Test
  void sectionAndKey() {
    final ConfigFile config = new ConfigBuilder()
      .section("server")
      .setString("host", "localhost")
      .endSection()
      .build(PropertiesFormat.INSTANCE);
    assertTrue(config.contains("server.host"));
    assertEquals("localhost", config.require(PropertyKey.string("server.host")));
  }

  @Test
  void dotInKeyUnderSectionThrows() {
    assertThrows(IllegalArgumentException.class,
      () -> new ConfigBuilder()
        .section("s")
        .setString("a.b", "x")
        .build(IniFormat.INSTANCE));
  }

  @Test
  void commentPrefixDefault() {
    final ConfigFile config = new ConfigBuilder()
      .comment("plain text")
      .build(PropertiesFormat.INSTANCE);
    final CommentNode node = (CommentNode) nodesOf(config).get(0);
    assertEquals("# plain text", node.text());
  }

  @Test
  void commentCharConfigurable() {
    final ConfigFile config = new ConfigBuilder()
      .commentChar(';')
      .comment("plain text")
      .build(PropertiesFormat.INSTANCE);
    final CommentNode node = (CommentNode) nodesOf(config).get(0);
    assertEquals("; plain text", node.text());
  }

  @Test
  void commentAlreadyMarkedVerbatim() {
    final ConfigFile config = new ConfigBuilder()
      .comment("# already marked")
      .comment("! bang")
      .comment("; semi")
      .build(PropertiesFormat.INSTANCE);
    final List<Node> nodes = nodesOf(config);
    assertEquals("# already marked", ((CommentNode) nodes.get(0)).text());
    assertEquals("! bang", ((CommentNode) nodes.get(1)).text());
    assertEquals("; semi", ((CommentNode) nodes.get(2)).text());
  }

  @Test
  void blankAfterValues() {
    final ConfigFile config = new ConfigBuilder()
      .blankAfterValues(true)
      .setString("a", "1")
      .setString("b", "2")
      .build(PropertiesFormat.INSTANCE);
    final List<Node> nodes = nodesOf(config);
    assertEquals(4, nodes.size()); // property, blank, property, blank
  }

  @Test
  void blankAfterValuesOff() {
    final ConfigFile config = new ConfigBuilder()
      .blankAfterValues(false)
      .setString("a", "1")
      .setString("b", "2")
      .build(PropertiesFormat.INSTANCE);
    assertEquals(2, nodesOf(config).size());
  }

  @Test
  void typedShortcuts() {
    final ConfigFile config = new ConfigBuilder()
      .setBoolean("debug", true)
      .setInt("port", 25565)
      .setLong("seed", 123456789012345L)
      .setDouble("ratio", 1.5)
      .setFloat("speed", 2.5f)
      .setChar("sep", ',')
      .setDuration("timeout", Duration.ofSeconds(30))
      .setList("hosts", "a", "b", "c")
      .build(PropertiesFormat.INSTANCE);
    assertTrue(config.require(PropertyKey.bool("debug")));
    assertEquals(25565, config.require(PropertyKey.integer("port")));
    assertEquals(123456789012345L, config.require(PropertyKey.longKey("seed")));
    assertEquals(1.5, config.require(PropertyKey.decimal("ratio")));
    assertEquals(2.5f, config.require(PropertyKey.floatKey("speed")));
    assertEquals(",", config.require(PropertyKey.string("sep")));
    assertEquals(Duration.ofSeconds(30),
      config.require(PropertyKey.duration("timeout")));
    assertEquals(List.of("a", "b", "c"),
      config.require(PropertyKey.stringList("hosts")));
  }

  @Test
  void setListFromVarargs() {
    final ConfigFile config = new ConfigBuilder()
      .setList("hosts", "a", "b")
      .build(PropertiesFormat.INSTANCE);
    assertEquals(List.of("a", "b"),
      config.require(PropertyKey.stringList("hosts")));
  }

  @Test
  void setListFromList() {
    final ConfigFile config = new ConfigBuilder()
      .setList("hosts", List.of("x", "y"))
      .build(PropertiesFormat.INSTANCE);
    assertEquals(List.of("x", "y"),
      config.require(PropertyKey.stringList("hosts")));
  }

  @Test
  void setEnum() {
    final ConfigFile config = new ConfigBuilder()
      .setEnum("mode", Color.RED)
      .build(PropertiesFormat.INSTANCE);
    assertEquals("RED", config.require(PropertyKey.string("mode")));
  }

  enum Color {RED, GREEN, BLUE}

  @Test
  void header() {
    final ConfigFile config = new ConfigBuilder()
      .header("Line one", "Line two")
      .build(PropertiesFormat.INSTANCE);
    final List<Node> nodes = nodesOf(config);
    assertEquals("# Line one", ((CommentNode) nodes.get(0)).text());
    assertEquals("# Line two", ((CommentNode) nodes.get(1)).text());
  }

  @Test
  void explicitSetHonoursBlankFlag() {
    final ConfigFile config = new ConfigBuilder()
      .blankAfterValues(true)
      .set(PropertyKey.string("a", "1"), "1", false)
      .build(PropertiesFormat.INSTANCE);
    assertEquals(1, nodesOf(config).size());
  }
}