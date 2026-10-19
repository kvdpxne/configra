package me.kvd.configra.format.ini;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import me.kvd.configra.CommentChars;
import me.kvd.configra.DuplicatePolicy;
import me.kvd.configra.DuplicateSectionPolicy;
import me.kvd.configra.KeyStyle;
import me.kvd.configra.escape.TextSupport;
import me.kvd.configra.exceptions.PropertyParseException;
import me.kvd.configra.options.ParseOptions;
import me.kvd.configra.options.WriteOptions;
import me.kvd.configra.spi.ConfigFormat;
import me.kvd.configra.spi.node.BlankNode;
import me.kvd.configra.spi.node.CommentNode;
import me.kvd.configra.spi.node.DisabledPropertyNode;
import me.kvd.configra.spi.node.Node;
import me.kvd.configra.spi.node.PropertyNode;
import me.kvd.configra.spi.node.SectionNode;

/**
 * INI syntax with sections {@code [name]}, {@code key = value} assignments
 * (both {@code =} and {@code :} accepted), and {@code ;} / {@code #}
 * comments.
 *
 * <p>Supported dialect quirks, all opt-in via {@link ParseOptions} and
 * {@link WriteOptions}: comment characters, case-insensitive sections and
 * keys, quoted values, inline comments, relative nesting
 * ({@code [.subsection]}), indented continuation (Python
 * {@code configparser} style), duplicate policy, duplicate-section policy,
 * and {@link KeyStyle} transformation.</p>
 *
 * <p><b>Write side.</b> Section and property keys stored in the node graph
 * are always canonical ({@link KeyStyle#CAMEL_CASE}). {@link WriteOptions#keyStyle()}
 * controls how they are emitted, so a round-trip through
 * {@code load(readOpts).save(writeOpts)} with matching styles reproduces
 * the input byte-for-byte.</p>
 *
 * <p><b>Duplicate sections.</b> The {@link DuplicateSectionPolicy#IGNORE}
 * policy drops the duplicate section's properties from the node graph
 * entirely; only the {@link SectionNode} is preserved for layout. This is
 * documented behaviour, not a bug.</p>
 */
public final class IniFormat implements ConfigFormat {

  public static final IniFormat INSTANCE = new IniFormat();

  private IniFormat() {
  }

  // -------------------------------------------------------------------
  // Parse
  // -------------------------------------------------------------------

  @Override
  public List<Node> parse(final String source) {
    return this.parse(source, ParseOptions.iniDefaults());
  }

  @Override
  public List<Node> parse(final String source, final ParseOptions options) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(options, "options");

    final List<Node> out = new ArrayList<>();
    final Set<String> seenSections = new HashSet<>();
    final Map<String, Integer> seenKeys = options.duplicatePolicy()
      == DuplicatePolicy.ERROR
      ? new HashMap<>()
      : null;

    // Canonical section for the node graph, raw section for relative
    // nesting expansion. They differ when keyStyle != NONE.
    String currentRawSection = null;
    String currentCanonicalSection = null;
    boolean currentSectionIgnored = false;

    // Python-style indented continuation: the previous property node
    // absorbs subsequent indented lines.
    PropertyNode pendingContinuation = null;

    int cursor = 0;
    int lineNumber = 0;
    final int length = source.length();

    while (cursor < length) {
      int lineEnd = cursor;
      while (lineEnd < length && '\n' != source.charAt(lineEnd)) {
        lineEnd++;
      }
      String line = source.substring(cursor, lineEnd);
      if (!line.isEmpty() && '\r' == line.charAt(line.length() - 1)) {
        line = line.substring(0, line.length() - 1);
      }
      cursor = lineEnd + 1;

      final boolean isIndented = !line.isEmpty()
        && (' ' == line.charAt(0) || '\t' == line.charAt(0));
      final String content = line.stripLeading();

      // ----- Indented continuation (Python ConfigParser style) -----
      if (options.values().indentedContinuation()
        && isIndented
        && null != pendingContinuation
        && !content.isEmpty()) {
        final char head = content.charAt(0);
        if (!options.comments().chars().matches(head) && '[' != head) {
          final String merged = pendingContinuation.value()
            + '\n' + TextSupport.stripTrailing(content);
          final PropertyNode updated = new PropertyNode(
            pendingContinuation.section(),
            pendingContinuation.key(),
            merged);
          out.set(out.size() - 1, updated);
          pendingContinuation = updated;
          lineNumber++;
          continue;
        }
      }

      // ----- Blank line -----
      if (content.isEmpty()) {
        out.add(BlankNode.INSTANCE);
        pendingContinuation = null;
        lineNumber++;
        continue;
      }

      final char first = content.charAt(0);

      // ----- Comment -----
      if (options.comments().chars().matches(first)) {
        out.add(new CommentNode(line));
        pendingContinuation = null;
        lineNumber++;
        continue;
      }

      // ----- Section header -----
      if ('[' == first && content.endsWith("]")) {
        String rawName = content.substring(1,
          content.length() - 1).strip();
        if (rawName.isEmpty()) {
          throw new PropertyParseException(
            "Empty INI section header",
            null, lineNumber, line);
        }

        // Relative nesting: [.subsection] expands under the current
        // raw section so keyStyle transformation applies uniformly.
        if (rawName.startsWith(".")) {
          if (!options.values().relativeNesting()) {
            throw new PropertyParseException(
              "Relative section '[" + rawName
                + "]' requires relativeNesting=true",
              null, lineNumber, line);
          }
          if (null == currentRawSection) {
            throw new PropertyParseException(
              "Relative section '[" + rawName
                + "]' has no parent",
              null, lineNumber, line);
          }
          rawName = currentRawSection + rawName;
        }

        final String canonicalName =
          options.keyStyle().toCanonical(rawName);
        final String lookupName = options.caseOptions().insensitiveSections()
          ? canonicalName.toLowerCase(Locale.ROOT)
          : canonicalName;

        if (seenSections.contains(lookupName)) {
          switch (options.duplicateSectionPolicy()) {
            case ABORT -> throw new PropertyParseException(
              "Duplicate section '[" + rawName + "]'",
              null, lineNumber, line);
            case IGNORE -> {
              // Preserve the header for layout but drop every
              // property that follows until the next section.
              currentRawSection = rawName;
              currentCanonicalSection = canonicalName;
              currentSectionIgnored = true;
              pendingContinuation = null;
              out.add(new SectionNode(canonicalName));
              lineNumber++;
              continue;
            }
            case MERGE -> {
              // Fall through: same effective section, keys
              // will be merged into the value map.
            }
          }
        } else {
          seenSections.add(lookupName);
        }

        currentRawSection = rawName;
        currentCanonicalSection = canonicalName;
        currentSectionIgnored = false;
        pendingContinuation = null;
        out.add(new SectionNode(canonicalName));
        lineNumber++;
        continue;
      }

      // ----- key = value -----
      final int sep = findSeparator(content);
      if (-1 == sep) {
        throw new PropertyParseException(
          "Missing '=' or ':' in INI line",
          null, lineNumber, line);
      }
      final String rawKey = TextSupport.stripTrailing(content.substring(0, sep));
      if (rawKey.isEmpty()) {
        throw new PropertyParseException(
          "Empty key in INI line",
          null, lineNumber, line);
      }

      String value = TextSupport.stripLeading(content.substring(sep + 1));
      if (options.comments().inline()) {
        value = stripInlineComment(value, options.comments().chars());
      }
      if (options.whitespace().trimTrailing()) {
        value = TextSupport.stripTrailing(value);
      }
      if (options.values().unquote()) {
        value = TextSupport.unquote(value);
      }

      final String canonicalKey =
        options.keyStyle().toCanonical(rawKey);

      // Duplicate detection uses the effective lookup name (case
      // folding applied on top of canonicalization).
      if (null != seenKeys) {
        final String lookupKey = options.caseOptions().insensitiveKeys()
          ? canonicalKey.toLowerCase(Locale.ROOT)
          : canonicalKey;
        final String qualified = null == currentCanonicalSection
          ? lookupKey
          : (options.caseOptions().insensitiveSections()
          ? currentCanonicalSection.toLowerCase(Locale.ROOT)
          : currentCanonicalSection) + '.' + lookupKey;
        final Integer previous = seenKeys.put(qualified, lineNumber);
        if (null != previous) {
          throw new PropertyParseException(
            "Duplicate key '" + rawKey
              + "' (first seen at line "
              + (previous + 1) + ')',
            null, lineNumber, line);
        }
      }

      if (currentSectionIgnored) {
        // Section was a duplicate under IGNORE policy; skip the
        // property node entirely (documented behaviour).
        pendingContinuation = null;
        lineNumber++;
        continue;
      }

      final PropertyNode node = new PropertyNode(
        currentCanonicalSection, canonicalKey, value, lineNumber);
      out.add(node);
      pendingContinuation = options.values().indentedContinuation() ? node : null;
      lineNumber++;
    }
    return List.copyOf(out);
  }

  /**
   * Strips an inline comment from {@code value}: a comment character
   * preceded by whitespace terminates the value. Returns the original
   * string when no inline comment is found.
   */
  private static String stripInlineComment(final String value,
                                           final CommentChars chars) {
    for (int i = 1; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (chars.matches(c)
        && Character.isWhitespace(value.charAt(i - 1))) {
        return TextSupport.stripTrailing(value.substring(0, i - 1));
      }
    }
    return value;
  }

  // -------------------------------------------------------------------
  // Write
  // -------------------------------------------------------------------

  @Override
  public String write(final List<Node> nodes) {
    return this.write(nodes, WriteOptions.ini());
  }

  @Override
  public String write(final List<Node> nodes, final WriteOptions options) {
    Objects.requireNonNull(nodes, "nodes");
    Objects.requireNonNull(options, "options");

    final String sep = options.lineSeparator();
    final KeyStyle style = options.keyStyle();
    final StringBuilder out = new StringBuilder(nodes.size() * 32);

    // Canonical section name of the most recently written header.
    // Comparing canonical values keeps layout correct even when
    // keyStyle produces visual aliases (e.g. "time-zone" vs "timezone").
    String lastCanonicalSection = null;

    for (final Node node : nodes) {
      switch (node) {
        case BlankNode ignored -> out.append(sep);

        case CommentNode(final String text) -> out.append(normaliseComment(text, options))
          .append(sep);

        case SectionNode(final String canonicalName) -> {
          out.append('[')
            .append(style.fromCanonical(canonicalName))
            .append(']')
            .append(sep);
          lastCanonicalSection = canonicalName;
        }

        case PropertyNode(
          final String section,
          final String key,
          final String value,
          final int line
        ) -> {
          if (null != section
            && !section.equals(lastCanonicalSection)) {
            out.append('[')
              .append(style.fromCanonical(section))
              .append(']')
              .append(sep);
            lastCanonicalSection = section;
          }
          out.append(style.fromCanonical(key))
            .append(" = ")
            .append(maybeQuote(value, options))
            .append(sep);
        }

        case DisabledPropertyNode(
          final String section,
          final String key,
          final String value,
          final char prefix,
          final boolean space,
          final int line
        ) -> {
          if (null != section
            && !section.equals(lastCanonicalSection)) {
            out.append('[')
              .append(style.fromCanonical(section))
              .append(']')
              .append(sep);
            lastCanonicalSection = section;
          }
          out.append(prefix);
          if (space) {
            out.append(' ');
          }
          out.append(style.fromCanonical(key))
            .append(" = ")
            .append(maybeQuote(value, options))
            .append(sep);
        }
      }
    }
    return out.toString();
  }

  private static String maybeQuote(final String value,
                                   final WriteOptions options) {
    return options.quoteValues() ? '"' + value + '"' : value;
  }

  /**
   * Rewrites a comment line so its first non-whitespace character is the
   * configured {@link WriteOptions#commentChar()}. Only runs when
   * {@link WriteOptions#normalizeComments()} is enabled.
   */
  private static String normaliseComment(final String text,
                                         final WriteOptions options) {
    if (!options.normalizeComments()) {
      return text;
    }
    int i = 0;
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      i++;
    }
    if (i == text.length()) {
      return text;
    }
    final char marker = text.charAt(i);
    if (options.commentChars().isPrimary(marker)) {
      return text;
    }
    if (options.commentChars().accepts(marker)) {
      return text.substring(0, i) + options.commentChar()
        + text.substring(i + 1);
    }
    final String prefix = options.commentChar()
      + (options.spaceAfterComment() ? " " : "");
    return text.substring(0, i) + prefix + text.substring(i);
  }

  // -------------------------------------------------------------------
  // Peek
  // -------------------------------------------------------------------

  @Override
  public Optional<String> peek(final Reader source, final String qualifiedKey)
    throws IOException {
    return this.peek(source, qualifiedKey, ParseOptions.iniDefaults());
  }

  @Override
  public Optional<String> peek(final Reader source,
                               final String qualifiedKey,
                               final ParseOptions options) throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(qualifiedKey, "qualifiedKey");
    Objects.requireNonNull(options, "options");

    try (BufferedReader reader = buffer(source)) {
      String currentRawSection = null;
      String currentCanonicalSection = null;
      String line;
      while (null != (line = reader.readLine())) {
        final String content = line.stripLeading();
        if (content.isEmpty()) {
          continue;
        }
        final char first = content.charAt(0);
        if (options.comments().chars().matches(first)) {
          continue;
        }
        if ('[' == first && content.endsWith("]")) {
          String rawName = content.substring(1,
            content.length() - 1).strip();
          if (rawName.startsWith(".")
            && options.values().relativeNesting()
            && null != currentRawSection) {
            rawName = currentRawSection + rawName;
          }
          currentRawSection = rawName;
          currentCanonicalSection =
            options.keyStyle().toCanonical(rawName);
          continue;
        }
        final int sep = findSeparator(content);
        if (-1 == sep) {
          continue;
        }
        final String rawKey = TextSupport.stripTrailing(content.substring(0, sep));
        final String canonicalKey =
          options.keyStyle().toCanonical(rawKey);
        final String lookupKey = options.caseOptions().insensitiveKeys()
          ? canonicalKey.toLowerCase(Locale.ROOT)
          : canonicalKey;
        final String sectionPart = null == currentCanonicalSection
          ? null
          : (options.caseOptions().insensitiveSections()
          ? currentCanonicalSection.toLowerCase(Locale.ROOT)
          : currentCanonicalSection);
        final String qualified = null == sectionPart
          ? lookupKey
          : sectionPart + '.' + lookupKey;

        if (qualifiedKey.equals(qualified)) {
          String value = TextSupport.stripLeading(content.substring(sep + 1));
          if (options.comments().inline()) {
            value = stripInlineComment(value,
              options.comments().chars());
          }
          if (options.whitespace().trimTrailing()) {
            value = TextSupport.stripTrailing(value);
          }
          if (options.values().unquote()) {
            value = TextSupport.unquote(value);
          }
          return Optional.of(value);
        }
      }
    }
    return Optional.empty();
  }

  // -------------------------------------------------------------------
  // Stream
  // -------------------------------------------------------------------

  @Override
  public Stream<Map.Entry<String, String>> stream(final Reader source) {
    return this.stream(source, ParseOptions.iniDefaults());
  }

  @Override
  public Stream<Map.Entry<String, String>> stream(final Reader source,
                                                  final ParseOptions options) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(options, "options");
    return StreamSupport.stream(
      Spliterators.spliteratorUnknownSize(
        new IniIterator(source, options),
        Spliterator.ORDERED | Spliterator.NONNULL),
      false);
  }

  // -------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------

  private static int findSeparator(final String line) {
    for (int i = 0; i < line.length(); i++) {
      final char c = line.charAt(i);
      if ('=' == c || ':' == c) {
        return i;
      }
    }
    return -1;
  }

  static BufferedReader buffer(final Reader source) {
    return source instanceof BufferedReader buffered
      ? buffered
      : new BufferedReader(source);
  }

  // -------------------------------------------------------------------
  // Iterator
  // -------------------------------------------------------------------

  private static final class IniIterator
    implements Iterator<Map.Entry<String, String>> {

    private final BufferedReader reader;
    private final ParseOptions options;
    private String currentRawSection;
    private String currentCanonicalSection;
    private Map.Entry<String, String> next;
    private boolean finished;

    IniIterator(final Reader source, final ParseOptions options) {
      this.reader = buffer(source);
      this.options = options;
    }

    @Override
    public boolean hasNext() {
      if (null != this.next) {
        return true;
      }
      if (this.finished) {
        return false;
      }
      this.next = this.readNext();
      if (null == this.next) {
        this.finished = true;
      }
      return null != this.next;
    }

    @Override
    public Map.Entry<String, String> next() {
      if (!this.hasNext()) {
        throw new NoSuchElementException();
      }
      final Map.Entry<String, String> result = this.next;
      this.next = null;
      return result;
    }

    private Map.Entry<String, String> readNext() {
      try {
        String line;
        while (null != (line = this.reader.readLine())) {
          final String content = line.stripLeading();
          if (content.isEmpty()) {
            continue;
          }
          final char first = content.charAt(0);
          if (this.options.comments().chars().matches(first)) {
            continue;
          }
          if ('[' == first && content.endsWith("]")) {
            String rawName = content.substring(1,
              content.length() - 1).strip();
            if (rawName.startsWith(".")
              && this.options.values().relativeNesting()
              && null != this.currentRawSection) {
              rawName = this.currentRawSection + rawName;
            }
            this.currentRawSection = rawName;
            this.currentCanonicalSection =
              this.options.keyStyle().toCanonical(rawName);
            continue;
          }
          final int sep = findSeparator(content);
          if (-1 == sep) {
            continue;
          }
          final String rawKey = TextSupport.stripTrailing(content.substring(0, sep));
          if (rawKey.isEmpty()) {
            continue;
          }
          String value = TextSupport.stripLeading(content.substring(sep + 1));
          if (this.options.comments().inline()) {
            value = stripInlineComment(value,
              this.options.comments().chars());
          }
          if (this.options.whitespace().trimTrailing()) {
            value = TextSupport.stripTrailing(value);
          }
          if (this.options.values().unquote()) {
            value = TextSupport.unquote(value);
          }
          final String canonicalKey =
            this.options.keyStyle().toCanonical(rawKey);
          final String lookupKey = this.options.caseOptions().insensitiveKeys()
            ? canonicalKey.toLowerCase(Locale.ROOT)
            : canonicalKey;
          final String sectionPart =
            null == this.currentCanonicalSection
              ? null
              : (this.options.caseOptions().insensitiveSections()
              ? this.currentCanonicalSection
              .toLowerCase(Locale.ROOT)
              : this.currentCanonicalSection);
          final String qualified = null == sectionPart
            ? lookupKey
            : sectionPart + '.' + lookupKey;
          return Map.entry(qualified, value);
        }
        return null;
      } catch (final IOException exception) {
        throw new UncheckedIOException(exception);
      }
    }
  }

  @Override
  public String name() {
    return "ini";
  }

  @Override
  public String extension() {
    return '.' + this.name();
  }
}