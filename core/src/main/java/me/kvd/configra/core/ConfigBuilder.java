package me.kvd.configra.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.kvd.configra.PropertyCodec;
import me.kvd.configra.CommentChars;
import me.kvd.configra.PropertyKey;
import me.kvd.configra.options.ParseOptions;
import me.kvd.configra.spi.ConfigFormat;
import me.kvd.configra.spi.node.BlankNode;
import me.kvd.configra.spi.node.CommentNode;
import me.kvd.configra.spi.node.Node;
import me.kvd.configra.spi.node.PropertyNode;
import me.kvd.configra.spi.node.SectionNode;

/**
 * Fluent builder for {@link ConfigFile} instances.
 *
 * <h2>Comment prefixing</h2>
 * <p>{@link #comment(String)} and {@link #header(String...)} automatically
 * prepend {@link #commentChar(char)} (default {@code '#'}) followed by a
 * space when the input does not already begin with a known marker
 * ({@code '#'}, {@code '!'} or {@code ';'}). Text carrying an explicit
 * marker is written verbatim.</p>
 *
 * <h2>Blank line after values</h2>
 * <p>{@link #blankAfterValues(boolean)} makes every subsequent
 * {@link #set(PropertyKey, Object)} (and its typed shortcuts) append an
 * empty line. Use it to produce a file where properties are visually
 * separated. Per-call control is available via
 * {@link #set(PropertyKey, Object, boolean)}.</p>
 *
 * <h2>Typed shortcuts</h2>
 * <p>{@code setString}, {@code setBoolean}, {@code setInt},
 * {@code setLong}, {@code setDouble}, {@code setChar}, {@code setEnum},
 * {@code setDuration} and {@code setStringList} take a plain name and a
 * value, derive a {@link PropertyKey} from the value's type and delegate
 * to {@link #set(PropertyKey, Object)}. They respect
 * {@link #blankAfterValues(boolean)}. For a value with a custom codec or a
 * declared default, use the explicit {@code set(PropertyKey, value)}
 * overload.</p>
 *
 * <h2>Duplicate detection</h2>
 * <p>Duplicate keys within the same section raise
 * {@link IllegalStateException}. Two sections may declare the same local
 * key. Keys declared outside a section are validated against each other.</p>
 */
public final class ConfigBuilder {

  private final List<Node> nodes = new ArrayList<>();
  private String currentSection;

  private char commentChar = '#';
  private boolean spaceAfterComment = true;
  private boolean autoPrefixComments = true;
  private boolean blankAfterValues = false;

  // ----- Comment configuration -----

  public ConfigBuilder commentChar(final char value) {
    if ('\0' == value) {
      throw new IllegalArgumentException("commentChar must not be NUL");
    }
    this.commentChar = value;
    return this;
  }

  public ConfigBuilder commentChar(final CommentChars commentChar) {
    Objects.requireNonNull(commentChar, "commentChar");
    return this.commentChar(commentChar.primary());
  }

  public ConfigBuilder spaceAfterComment(final boolean value) {
    this.spaceAfterComment = value;
    return this;
  }

  public ConfigBuilder autoPrefixComments(final boolean value) {
    this.autoPrefixComments = value;
    return this;
  }

  // ----- Blank-line behaviour -----

  /**
   * When {@code true}, every subsequent {@code set(...)} appends an empty
   * line after the key-value pair. Defaults to {@code false}.
   *
   * <p>The flag affects only the typed shortcut methods and the two-arg
   * {@code set(PropertyKey, value)}. Use
   * {@link #set(PropertyKey, Object, boolean)} for per-call control
   * irrespective of the current flag.</p>
   */
  public ConfigBuilder blankAfterValues(final boolean value) {
    this.blankAfterValues = value;
    return this;
  }

  public ConfigBuilder header(final String... lines) {
    Objects.requireNonNull(lines, "lines");
    for (final String line : lines) {
      Objects.requireNonNull(line, "line");
      this.nodes.add(new CommentNode(this.prepare(line)));
    }
    return this;
  }

  public ConfigBuilder comment(final String text) {
    Objects.requireNonNull(text, "text");
    this.nodes.add(new CommentNode(this.prepare(text)));
    return this;
  }

  public ConfigBuilder blank() {
    this.nodes.add(BlankNode.INSTANCE);
    return this;
  }

  public ConfigBuilder section(final String name) {
    Objects.requireNonNull(name, "name");
    this.nodes.add(new SectionNode(name));
    this.currentSection = name;
    return this;
  }

  public ConfigBuilder endSection() {
    this.currentSection = null;
    return this;
  }

  /**
   * Adds a typed pair, honouring {@link #blankAfterValues(boolean)}.
   */
  public <T> ConfigBuilder set(final PropertyKey<T> key, final T value) {
    return this.set(key, value, this.blankAfterValues);
  }

  /**
   * Adds a typed pair with explicit control over the trailing blank line.
   */
  public <T> ConfigBuilder set(final PropertyKey<T> key,
                               final T value,
                               final boolean blankAfter) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    if (null != this.currentSection && key.name().contains(".")) {
      throw new IllegalArgumentException(
        "Key '" + key.name()
          + "' must not contain '.' when inside section '"
          + this.currentSection
          + "'. Use a nested section instead.");
    }
    this.nodes.add(new PropertyNode(
      this.currentSection, key.name(), key.codec().encode(value)));
    if (blankAfter) {
      this.nodes.add(BlankNode.INSTANCE);
    }
    return this;
  }

  /**
   * Equivalent to {@code set(PropertyKey.bool(name), value)}.
   */
  public ConfigBuilder setBoolean(final String name, final boolean value) {
    return this.set(PropertyKey.bool(name), value);
  }

  /**
   * Equivalent to {@code set(PropertyKey.integer(name), value)}.
   */
  public ConfigBuilder setInt(final String name, final int value) {
    return this.set(PropertyKey.integer(name), value);
  }

  /**
   * Equivalent to {@code set(PropertyKey.longKey(name), value)}.
   */
  public ConfigBuilder setLong(final String name, final long value) {
    return this.set(PropertyKey.longKey(name), value);
  }

  /**
   * Equivalent to {@code set(PropertyKey.floatKey(name), value)}.
   *
   * <p>See {@link PropertyCodec#FLOAT} for the
   * precision caveat. Prefer {@link #setDouble} unless the target field
   * is genuinely {@code float}.</p>
   */
  public ConfigBuilder setFloat(final String name, final float value) {
    return this.set(PropertyKey.floatKey(name), value);
  }

  /**
   * Equivalent to {@code set(PropertyKey.decimal(name), value)}.
   */
  public ConfigBuilder setDouble(final String name, final double value) {
    return this.set(PropertyKey.decimal(name), value);
  }

  /**
   * Equivalent to {@code set(PropertyKey.string(name), value)}.
   */
  public ConfigBuilder setString(final String name, final String value) {
    return this.set(PropertyKey.string(name), value);
  }

  /**
   * Stores a single character as a one-character string, since the
   * underlying formats are text-based. Equivalent to
   * {@code set(PropertyKey.string(name), String.valueOf(value))}.
   */
  public ConfigBuilder setChar(final String name, final char value) {
    return this.setString(name, String.valueOf(value));
  }

  /**
   * Adds a string list from varargs or a {@code String[]}. The values are
   * always stored internally as a {@code List<String>} — the array is
   * defensively copied through {@link List#of(Object[])} so later mutation
   * of the source has no effect on the built file.
   *
   * <pre>{@code
   * builder.setList("hosts", "alpha", "beta", "gamma");
   * builder.setList("hosts", new String[]{"alpha", "beta", "gamma"});
   * }</pre>
   */
  public ConfigBuilder setList(final String name, final String... values) {
    Objects.requireNonNull(values, "values");
    return this.set(PropertyKey.stringList(name), List.of(values));
  }

  /**
   * Adds a string list from an existing {@link List}. The list is copied
   * by the codec's encoder, so mutating it afterwards does not affect the
   * built file.
   */
  public ConfigBuilder setList(final String name, final List<String> values) {
    Objects.requireNonNull(values, "values");
    return this.set(PropertyKey.stringList(name), values);
  }

  /**
   * Equivalent to {@code set(PropertyKey.duration(name), value)}.
   */
  public ConfigBuilder setDuration(final String name, final Duration value) {
    return this.set(PropertyKey.duration(name), value);
  }

  /**
   * Encodes {@code value} using the codec derived from its enum class.
   * The value itself is used as the key's default, which is irrelevant
   * for {@code set} but keeps the method signature uniform.
   */
  public <E extends Enum<E>> ConfigBuilder setEnum(final String name,
                                                   final E value) {
    Objects.requireNonNull(value, "value");
    final Class<E> type = value.getDeclaringClass();
    return this.set(PropertyKey.enumeration(name, type, value), value);
  }

  public ConfigFile build(final ConfigFormat format) {
    Objects.requireNonNull(format, "format");
    return this.build(format, ConfigFileIO.defaultParseOptions(format));
  }

  public ConfigFile build(final ConfigFormat format,
                          final ParseOptions parseOptions) {
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(parseOptions, "parseOptions");
    return new ConfigFile(this.nodes, null, format, parseOptions);
  }

  // ----- Internals -----

  private String prepare(final String text) {
    if (!this.autoPrefixComments) {
      return text;
    }
    if (this.hasCommentMarker(text)) {
      return text;
    }
    if (this.spaceAfterComment) {
      return this.commentChar + " " + text;
    }
    return this.commentChar + text;
  }

  private boolean hasCommentMarker(final String text) {
    if (text.isEmpty()) {
      return false;
    }
    final char first = text.charAt(0);
    return '#' == first || '!' == first || ';' == first;
  }
}