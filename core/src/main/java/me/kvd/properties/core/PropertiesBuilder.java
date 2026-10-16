package me.kvd.properties.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import me.kvd.properties.PropertyKey;
import me.kvd.properties.options.ParseOptions;
import me.kvd.properties.spi.ConfigFormat;
import me.kvd.properties.spi.node.BlankNode;
import me.kvd.properties.spi.node.CommentNode;
import me.kvd.properties.spi.node.Node;
import me.kvd.properties.spi.node.PropertyNode;
import me.kvd.properties.spi.node.SectionNode;

/**
 * Fluent builder for {@link ConfigFile} instances.
 *
 * <p>Duplicate detection is scoped to the current section: two sections may
 * declare the same local key without conflict, matching INI semantics where
 * {@code [a] name} and {@code [b] name} are distinct properties.</p>
 */
public final class PropertiesBuilder {

  private final List<Node> nodes = new ArrayList<>();
  private String currentSection;

  public PropertiesBuilder header(final String... lines) {
    Objects.requireNonNull(lines, "lines");
    for (final String line : lines) {
      Objects.requireNonNull(line, "line");
      final String text = line.startsWith("#") || line.startsWith("!")
        || line.startsWith(";")
        ? line
        : "# " + line;
      this.nodes.add(new CommentNode(text));
    }
    return this;
  }

  public PropertiesBuilder comment(final String text) {
    Objects.requireNonNull(text, "text");
    this.nodes.add(new CommentNode(text));
    return this;
  }

  public PropertiesBuilder blank() {
    this.nodes.add(BlankNode.INSTANCE);
    return this;
  }

  public PropertiesBuilder section(final String name) {
    Objects.requireNonNull(name, "name");
    this.nodes.add(new SectionNode(name));
    this.currentSection = name;
    return this;
  }

  public PropertiesBuilder endSection() {
    this.currentSection = null;
    return this;
  }

  public <T> PropertiesBuilder set(final PropertyKey<T> key, final T value) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");

    if (null != this.currentSection && key.name().contains(".")) {
      throw new IllegalArgumentException(
        "Key '" + key.name()
          + "' must not contain '.' when inside section '"
          + this.currentSection
          + "'. Use a nested section via section(...) instead.");
    }
    this.nodes.add(new PropertyNode(
      this.currentSection, key.name(), key.codec().encode(value)));
    return this;
  }

  public ConfigFile build(final ConfigFormat format) {
    return this.build(format, ParseOptions.defaults());
  }

  public ConfigFile build(final ConfigFormat format,
                          final ParseOptions parseOptions) {
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(parseOptions, "parseOptions");
    return new ConfigFile(this.nodes, null, format, parseOptions);
  }
}