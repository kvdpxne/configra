package me.kvd.properties.format.properties;

import java.util.List;
import me.kvd.properties.DuplicatePolicy;
import me.kvd.properties.exceptions.PropertyParseException;
import me.kvd.properties.options.ParseOptions;
import me.kvd.properties.spi.node.BlankNode;
import me.kvd.properties.spi.node.CommentNode;
import me.kvd.properties.spi.node.Node;
import me.kvd.properties.spi.node.PropertyNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PropertiesFormatTest {

  private static List<Node> parse(final String source) {
    return PropertiesFormat.INSTANCE.parse(source);
  }

  private static PropertyNode firstProperty(final List<Node> nodes) {
    return nodes.stream()
      .filter(PropertyNode.class::isInstance)
      .map(PropertyNode.class::cast)
      .findFirst()
      .orElseThrow();
  }

  @Test
  void simpleKeyValue() {
    final List<Node> nodes = parse("key=value\n");
    final PropertyNode property = firstProperty(nodes);
    assertEquals("key", property.key());
    assertEquals("value", property.value());
  }

  @Test
  void whitespaceAroundEquals() {
    final List<Node> nodes = parse("key = value\n");
    final PropertyNode property = firstProperty(nodes);
    assertEquals("key", property.key());
    assertEquals("value", property.value());
  }

  @Test
  void colonSeparator() {
    final PropertyNode property = firstProperty(parse("key:value\n"));
    assertEquals("key", property.key());
    assertEquals("value", property.value());
  }

  @Test
  void whitespaceSeparator() {
    final PropertyNode property = firstProperty(parse("key value\n"));
    assertEquals("key", property.key());
    assertEquals("value", property.value());
  }

  @Test
  void comment() {
    final List<Node> nodes = parse("# comment\n");
    assertInstanceOf(CommentNode.class, nodes.get(0));
  }

  @Test
  void bangComment() {
    final List<Node> nodes = parse("! bang comment\n");
    assertInstanceOf(CommentNode.class, nodes.get(0));
  }

  @Test
  void indentedComment() {
    final List<Node> nodes = parse("  # indented comment\n");
    assertInstanceOf(CommentNode.class, nodes.get(0));
  }

  @Test
  void blankLine() {
    final List<Node> nodes = parse("\n");
    assertInstanceOf(BlankNode.class, nodes.get(0));
  }

  @Test
  void escapedEqualsInKey() {
    final PropertyNode property = firstProperty(
      parse("a\\=b=c\n"));
    assertEquals("a=b", property.key());
    assertEquals("c", property.value());
  }

  @Test
  void keyWithoutValue() {
    final PropertyNode property = firstProperty(parse("empty\n"));
    assertEquals("empty", property.key());
    assertEquals("", property.value());
  }

  @Test
  void lineContinuation() {
    final PropertyNode property = firstProperty(
      parse("key=line1 \\\n   line2\n"));
    assertEquals("line1 line2", property.value());
  }

  @Test
  void evenBackslashesNoContinuation() {
    final PropertyNode property = firstProperty(
      parse("key=value\\\\\n# comment\n"));
    assertEquals("value\\", property.value());
  }

  @Test
  void unicodeEscape() {
    final PropertyNode property = firstProperty(
      parse("key=\\u0041\\u0042\n"));
    assertEquals("AB", property.value());
  }

  @Test
  void utf8Value() {
    final PropertyNode property = firstProperty(
      parse("key=こんにちは\n"));
    assertEquals("こんにちは", property.value());
  }

  @Test
  void duplicateLastWins() {
    final ParseOptions options = ParseOptions.builder()
      .duplicatePolicy(DuplicatePolicy.LAST_WINS)
      .build();
    final List<PropertyNode> props = PropertiesFormat.INSTANCE
      .parse("a=1\na=2\n", options)
      .stream()
      .filter(PropertyNode.class::isInstance)
      .map(PropertyNode.class::cast)
      .toList();
    assertEquals(2, props.size());
  }

  @Test
  void duplicateErrorThrows() {
    final ParseOptions options = ParseOptions.builder()
      .duplicatePolicy(DuplicatePolicy.ERROR)
      .build();
    assertThrows(PropertyParseException.class,
      () -> PropertiesFormat.INSTANCE.parse("a=1\na=2\n", options));
  }

  @Test
  void emptyKeyThrows() {
    assertThrows(PropertyParseException.class, () -> parse("=value\n"));
  }

  @Test
  void writeSimple() {
    final List<Node> nodes = List.of(new PropertyNode(null, "key", "value"));
    assertEquals("key=value\n", PropertiesFormat.INSTANCE.write(nodes));
  }

  @Test
  void roundTripPreservesValues() {
    final String source = "a=1\nb=2\n";
    final List<Node> parsed = parse(source);
    final String written = PropertiesFormat.INSTANCE.write(parsed);
    final List<Node> reparsed = parse(written);
    assertEquals(
      firstProperty(parsed).value(),
      firstProperty(reparsed).value());
  }
}