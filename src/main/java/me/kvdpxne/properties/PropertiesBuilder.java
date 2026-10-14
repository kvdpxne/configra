package me.kvdpxne.properties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fluent builder for {@link ConfigFile} instances.
 *
 * <p>Duplicate detection is scoped to the current section: two sections may
 * declare the same local key without conflict, matching INI semantics where
 * {@code [a] name} and {@code [b] name} are distinct properties.</p>
 */
public final class PropertiesBuilder {

  private final List<Node> nodes = new ArrayList<>();
  private final Set<String> declaredKeys = new HashSet<>();
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

    // Duplicate detection is per-section. Two sections may declare the
    // same local key; only a repeated key inside one section is an error.
    final String qualified = null == this.currentSection
      ? key.name()
      : this.currentSection + '.' + key.name();
    if (!this.declaredKeys.add(qualified)) {
      throw new IllegalStateException("Duplicate key: " + qualified);
    }

    this.nodes.add(new PropertyNode(
      this.currentSection, key.name(), key.codec().encode(value)));
    return this;
  }

  public ConfigFile build() {
    return this.build(ConfigFormat.PROPERTIES);
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