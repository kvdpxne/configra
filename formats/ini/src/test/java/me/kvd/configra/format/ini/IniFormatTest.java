package me.kvd.configra.format.ini;

import java.util.List;
import me.kvd.configra.DuplicateSectionPolicy;
import me.kvd.configra.exceptions.PropertyParseException;
import me.kvd.configra.options.ParseOptions;
import me.kvd.configra.spi.node.BlankNode;
import me.kvd.configra.spi.node.CommentNode;
import me.kvd.configra.spi.node.Node;
import me.kvd.configra.spi.node.PropertyNode;
import me.kvd.configra.spi.node.SectionNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IniFormatTest {

  private static List<Node> parse(final String source) {
    return IniFormat.INSTANCE.parse(source);
  }

  @Test
  void sectionAndProperty() {
    final List<Node> nodes = parse("[general]\nkey = value\n");
    assertInstanceOf(SectionNode.class, nodes.get(0));
    final PropertyNode property = (PropertyNode) nodes.get(1);
    assertEquals("general", property.section());
    assertEquals("key", property.key());
    assertEquals("value", property.value());
    assertEquals("general.key", property.qualifiedKey());
  }

  @Test
  void multipleSections() {
    final List<Node> nodes = parse("[a]\nk=1\n[b]\nk=2\n");
    assertEquals(4, nodes.size());
  }

  @Test
  void semicolonComment() {
    final List<Node> nodes = parse("; comment\n");
    assertInstanceOf(CommentNode.class, nodes.get(0));
  }

  @Test
  void hashComment() {
    final List<Node> nodes = parse("# comment\n");
    assertInstanceOf(CommentNode.class, nodes.get(0));
  }

  @Test
  void blankLine() {
    final List<Node> nodes = parse("\n");
    assertInstanceOf(BlankNode.class, nodes.get(0));
  }

  @Test
  void emptySectionThrows() {
    assertThrows(PropertyParseException.class, () -> parse("[]\n"));
  }

  @Test
  void duplicateMerge() {
    final ParseOptions options = ParseOptions.builder()
      .duplicateSectionPolicy(DuplicateSectionPolicy.MERGE)
      .build();
    final List<Node> nodes = IniFormat.INSTANCE.parse(
      "[a]\nk=1\n[a]\nk=2\n", options);
    // Both property nodes retained; value map will resolve later
    assertEquals(4, nodes.size());
  }

  @Test
  void duplicateAbort() {
    final ParseOptions options = ParseOptions.builder()
      .duplicateSectionPolicy(DuplicateSectionPolicy.ABORT)
      .build();
    assertThrows(PropertyParseException.class,
      () -> IniFormat.INSTANCE.parse("[a]\nk=1\n[a]\n", options));
  }

  @Test
  void colonSeparator() {
    final List<Node> nodes = parse("[s]\nk: v\n");
    final PropertyNode p = (PropertyNode) nodes.get(1);
    assertEquals("k", p.key());
    assertEquals("v", p.value());
  }

  @Test
  void missingSeparatorThrows() {
    assertThrows(PropertyParseException.class,
      () -> parse("[s]\nbad line\n"));
  }

  @Test
  void writeSection() {
    final List<Node> nodes = List.of(
      new SectionNode("a"),
      new PropertyNode("a", "k", "v"));
    final String written = IniFormat.INSTANCE.write(nodes);
    assertTrue(written.contains("[a]"));
    assertTrue(written.contains("k = v"));
  }

  @Test
  void roundTrip() {
    final String source = "; comment\n[a]\nk = v\n\n[b]\nk2 = v2\n";
    final List<Node> parsed = parse(source);
    final String written = IniFormat.INSTANCE.write(parsed);
    final List<Node> reparsed = parse(written);
    assertEquals(parsed.size(), reparsed.size());
  }
}