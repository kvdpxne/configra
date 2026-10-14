package me.kvdpxne.properties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import me.kvdpxne.properties.exceptions.PropertyParseException;

final class PropertiesFormat implements ConfigFormat {

  static final PropertiesFormat INSTANCE = new PropertiesFormat();

  private PropertiesFormat() {
  }

  // ----- Parse -----

  @Override
  public List<Node> parse(final String source) {
    return this.parse(source, ParseOptions.defaults());
  }

  @Override
  public List<Node> parse(final String source, final ParseOptions options) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(options, "options");
    final String normalised = normaliseLineEndings(source);
    final List<Node> out = new ArrayList<>();
    final Map<String, Integer> seen = options.duplicatePolicy()
      == DuplicatePolicy.ERROR
      ? new HashMap<>()
      : null;

    int lineNumber = 0;
    for (final LogicalLine logical : splitLogicalLines(normalised, options)) {
      final String line = logical.text();

      int firstNonWs = 0;
      while (firstNonWs < line.length()
        && Character.isWhitespace(line.charAt(firstNonWs))) {
        firstNonWs++;
      }

      if (firstNonWs == line.length()) {
        out.add(BlankNode.INSTANCE);
        lineNumber++;
        continue;
      }

      if (options.commentChars().matches(line.charAt(firstNonWs))) {
        if (options.parseDisabledProperties()) {
          final DisabledPropertyNode disabled =
            tryParseDisabled(line, firstNonWs, options);
          if (null != disabled) {
            out.add(disabled);
            lineNumber++;
            continue;
          }
        }
        out.add(new CommentNode(line));
        lineNumber++;
        continue;
      }

      final PropertyNode property = parseProperty(logical, options);
      if (null != seen) {
        final Integer previous = seen.put(
          property.qualifiedKey(), lineNumber);
        if (null != previous) {
          throw new PropertyParseException(
            "Duplicate key '" + property.key()
              + "' (first seen at line "
              + (previous + 1) + ')',
            null, lineNumber, line);
        }
      } else if (options.duplicatePolicy() == DuplicatePolicy.FIRST_WINS) {
        // Handled in ConfigFile via a "keep first" pass. Here we
        // only care that the node list preserves the raw order.
      }
      out.add(property);
      lineNumber++;
    }
    return List.copyOf(out);
  }

  static String normaliseLineEndings(final String source) {
    if (-1 == source.indexOf('\r')) {
      return source;
    }
    return source.replace("\r\n", "\n").replace('\r', '\n');
  }

  // ----- Write -----

  @Override
  public String write(final List<Node> nodes) {
    return this.write(nodes, WriteOptions.utf8());
  }

  @Override
  public String write(final List<Node> nodes, final WriteOptions options) {
    Objects.requireNonNull(nodes, "nodes");
    Objects.requireNonNull(options, "options");
    final String sep = options.lineSeparator();
    final boolean escapeNonAscii = options.escapeNonAscii();
    final boolean quote = options.quoteValues();
    final StringBuilder out = new StringBuilder(nodes.size() * 32);
    for (final Node node : nodes) {
      switch (node) {
        case BlankNode ignored -> out.append(sep);
        case CommentNode(final String text) -> out.append(normaliseComment(text, options)).append(sep);
        case SectionNode ignored -> {
          // .properties has no sections.
        }
        case PropertyNode(final String section, final String key,
                          final String value) -> {
          final String effective = null == section ? key : section + '.' + key;
          final String styledKey = options.keyStyle().fromCanonical(effective);
          out.append(EscapeCodec.escapeKey(styledKey, escapeNonAscii))
            .append('=')
            .append(escapeValueForWrite(value, escapeNonAscii, quote))
            .append(sep);
        }
        case DisabledPropertyNode(
          final String section, final String key,
          final String value, final char prefix,
          final boolean space
        ) -> {
          final String effective = null == section ? key : section + '.' + key;
          final String styledKey = options.keyStyle().fromCanonical(effective);
          out.append(prefix);
          if (space) {
            out.append(' ');
          }
          out.append(EscapeCodec.escapeKey(styledKey, escapeNonAscii))
            .append('=')
            .append(escapeValueForWrite(value, escapeNonAscii, quote))
            .append(sep);
        }
      }
    }
    return out.toString();
  }

  private static String escapeValueForWrite(final String value,
                                            final boolean escapeNonAscii,
                                            final boolean quote) {
    final String escaped =
      EscapeCodec.escapeValue(value, escapeNonAscii);
    return quote ? '"' + escaped + '"' : escaped;
  }

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
    // Leading marker is not a comment char; prepend one.
    final String prefix = options.commentChar()
      + (options.spaceAfterComment() ? " " : "");
    return text.substring(0, i) + prefix + text.substring(i);
  }

  // ----- Peek -----

  @Override
  public Optional<String> peek(final Reader source, final String qualifiedKey)
    throws IOException {
    return this.peek(source, qualifiedKey, ParseOptions.defaults());
  }

  @Override
  public Optional<String> peek(final Reader source,
                               final String qualifiedKey,
                               final ParseOptions options) throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(qualifiedKey, "qualifiedKey");
    Objects.requireNonNull(options, "options");
    try (BufferedReader reader = buffer(source)) {
      final StringBuilder current = new StringBuilder(128);
      String line;
      while (null != (line = reader.readLine())) {
        if (!current.isEmpty() && endsWithUnescapedBackslash(current)) {
          current.setLength(current.length() - 1);
          line = line.stripLeading();
        }
        current.append(line);
        if (endsWithUnescapedBackslash(current)) {
          continue;
        }
        final String logical = current.toString();
        current.setLength(0);
        final int eq = findKeyEnd(logical, options);
        if (-1 == eq) {
          continue;
        }
        final String candidate = unescapeKeySegment(logical, eq);
        if (qualifiedKey.equals(candidate)) {
          return Optional.of(unescapeValue(logical, eq, options));
        }
      }
    }
    return Optional.empty();
  }

  // ----- Stream -----

  @Override
  public Stream<Map.Entry<String, String>> stream(final Reader source) {
    return this.stream(source, ParseOptions.defaults());
  }

  @Override
  public Stream<Map.Entry<String, String>> stream(final Reader source,
                                                  final ParseOptions options) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(options, "options");
    return StreamSupport.stream(
      Spliterators.spliteratorUnknownSize(
        new PropertiesIterator(source, options),
        Spliterator.ORDERED | Spliterator.NONNULL),
      false);
  }

  // ----- Helpers -----

  static boolean isDelimiterWhitespace(final char c,
                                       final ParseOptions options) {
    if (' ' == c || '\t' == c || '\f' == c) {
      return true;
    }
    return !options.strictDelimiterWhitespace()
      && Character.isWhitespace(c);
  }

  record LogicalLine(int lineNumber, String text) {
  }

  private static List<LogicalLine> splitLogicalLines(
    final String source, final ParseOptions options) {
    final List<LogicalLine> result = new ArrayList<>();
    final StringBuilder current = new StringBuilder(128);
    final int length = source.length();
    boolean continuation = false;
    int cursor = 0;
    int physicalLine = 0;
    int logicalStart = 0;

    while (cursor < length) {
      int lineEnd = cursor;
      while (lineEnd < length && '\n' != source.charAt(lineEnd)) {
        lineEnd++;
      }
      String line = source.substring(cursor, lineEnd);
      cursor = lineEnd + 1;

      if (continuation) {
        line = line.stripLeading();
      } else {
        logicalStart = physicalLine;
      }
      current.append(line);

      if (endsWithUnescapedBackslash(current)) {
        current.setLength(current.length() - 1);
        continuation = true;
        physicalLine++;
        continue;
      }

      result.add(new LogicalLine(logicalStart, current.toString()));
      current.setLength(0);
      continuation = false;
      physicalLine++;
    }

    if (0 < current.length()) {
      if (continuation) {
        current.append('\\');
      }
      result.add(new LogicalLine(logicalStart, current.toString()));
    }
    return result;
  }

  private static PropertyNode parseProperty(final LogicalLine logical,
                                            final ParseOptions options) {
    final String line = logical.text();
    final int length = line.length();
    int cursor = 0;

    while (cursor < length
      && isDelimiterWhitespace(line.charAt(cursor), options)) {
      cursor++;
    }

    final StringBuilder rawKey = new StringBuilder(32);
    while (cursor < length) {
      final char c = line.charAt(cursor);
      if ('\\' == c && cursor + 1 < length) {
        rawKey.append(c).append(line.charAt(cursor + 1));
        cursor += 2;
        continue;
      }
      if ('=' == c || ':' == c
        || isDelimiterWhitespace(c, options)) {
        break;
      }
      rawKey.append(c);
      cursor++;
    }

    while (cursor < length
      && isDelimiterWhitespace(line.charAt(cursor), options)) {
      cursor++;
    }
    if (cursor < length && ('=' == line.charAt(cursor)
      || ':' == line.charAt(cursor))) {
      cursor++;
      while (cursor < length
        && isDelimiterWhitespace(line.charAt(cursor), options)) {
        cursor++;
      }
    }

    final String decodedKey = EscapeCodec.unescape(rawKey.toString());
    if (decodedKey.isEmpty()) {
      throw new PropertyParseException(
        "Empty key", null, logical.lineNumber(), line);
    }

    final String canonicalKey = options.keyStyle().toCanonical(decodedKey);
    String rawValue = cursor < length ? line.substring(cursor) : "";
    if (options.trimTrailingWhitespace()) {
      rawValue = trimTrailingDelimiterWhitespace(rawValue, options);
    }
    final String decodedValue = EscapeCodec.unescape(rawValue);
    final String finalValue = options.unquoteValues()
      ? unquote(decodedValue)
      : decodedValue;
    return new PropertyNode(null, canonicalKey, finalValue);
  }

  static String unquote(final String value) {
    if (2 > value.length()) {
      return value;
    }
    final char first = value.charAt(0);
    final char last = value.charAt(value.length() - 1);
    if ((first == '"' || first == '\'') && first == last) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static DisabledPropertyNode tryParseDisabled(final String line,
                                                       final int commentStart,
                                                       final ParseOptions options) {
    final char prefix = line.charAt(commentStart);
    int cursor = commentStart + 1;
    boolean space = false;
    if (cursor < line.length() && ' ' == line.charAt(cursor)) {
      space = true;
      cursor++;
    }
    if (cursor >= line.length()) {
      return null;
    }
    int eq = -1;
    for (int i = cursor; i < line.length(); i++) {
      final char c = line.charAt(i);
      if ('=' == c || ':' == c) {
        eq = i;
        break;
      }
      if (' ' == c || '\t' == c) {
        return null;
      }
    }
    if (eq < 0) {
      return null;
    }
    final String rawKey = line.substring(cursor, eq);
    if (rawKey.isEmpty()) {
      return null;
    }
    final String rawValue = eq + 1 < line.length()
      ? line.substring(eq + 1)
      : "";
    return new DisabledPropertyNode(
      null,
      EscapeCodec.unescape(rawKey),
      EscapeCodec.unescape(rawValue),
      prefix,
      space);
  }

  private static String trimTrailingDelimiterWhitespace(
    final String text, final ParseOptions options) {
    int end = text.length();
    while (0 < end
      && isDelimiterWhitespace(text.charAt(end - 1), options)) {
      end--;
    }
    return text.substring(0, end);
  }

  private static int findKeyEnd(final String line,
                                final ParseOptions options) {
    int cursor = 0;
    while (cursor < line.length()
      && isDelimiterWhitespace(line.charAt(cursor), options)) {
      cursor++;
    }
    if (cursor == line.length()) {
      return -1;
    }
    final char first = line.charAt(cursor);
    if (options.commentChars().matches(first)) {
      return -1;
    }
    final StringBuilder rawKey = new StringBuilder(32);
    while (cursor < line.length()) {
      final char c = line.charAt(cursor);
      if ('\\' == c && cursor + 1 < line.length()) {
        rawKey.append(c).append(line.charAt(cursor + 1));
        cursor += 2;
        continue;
      }
      if ('=' == c || ':' == c
        || isDelimiterWhitespace(c, options)) {
        break;
      }
      rawKey.append(c);
      cursor++;
    }
    return rawKey.isEmpty() ? -1 : cursor;
  }

  private static String unescapeKeySegment(final String line,
                                           final int end) {
    int cursor = 0;
    while (cursor < line.length()
      && Character.isWhitespace(line.charAt(cursor))) {
      cursor++;
    }
    return EscapeCodec.unescape(line.substring(cursor, end));
  }

  private static String unescapeValue(final String line,
                                      final int keyEnd,
                                      final ParseOptions options) {
    int cursor = keyEnd;
    while (cursor < line.length()
      && isDelimiterWhitespace(line.charAt(cursor), options)) {
      cursor++;
    }
    if (cursor < line.length() && ('=' == line.charAt(cursor)
      || ':' == line.charAt(cursor))) {
      cursor++;
      while (cursor < line.length()
        && isDelimiterWhitespace(line.charAt(cursor), options)) {
        cursor++;
      }
    }
    String raw = cursor < line.length() ? line.substring(cursor) : "";
    if (options.trimTrailingWhitespace()) {
      raw = trimTrailingDelimiterWhitespace(raw, options);
    }
    final String decoded = EscapeCodec.unescape(raw);
    return options.unquoteValues() ? unquote(decoded) : decoded;
  }

  static BufferedReader buffer(final Reader source) {
    return source instanceof BufferedReader buffered
      ? buffered
      : new BufferedReader(source);
  }

  static boolean endsWithUnescapedBackslash(final CharSequence text) {
    int count = 0;
    for (int i = text.length() - 1; 0 <= i && '\\' == text.charAt(i); i--) {
      count++;
    }
    return 0 != (count & 1);
  }

  // ----- Iterator -----

  private static final class PropertiesIterator
    implements Iterator<Map.Entry<String, String>> {

    private final BufferedReader reader;
    private final ParseOptions options;
    private final ArrayDeque<Map.Entry<String, String>> buffer =
      new ArrayDeque<>();
    private final StringBuilder current = new StringBuilder(128);
    private boolean finished;

    PropertiesIterator(final Reader source, final ParseOptions options) {
      this.reader = buffer(source);
      this.options = options;
    }

    @Override
    public boolean hasNext() {
      if (!this.buffer.isEmpty()) {
        return true;
      }
      if (this.finished) {
        return false;
      }
      this.fill();
      return !this.buffer.isEmpty();
    }

    @Override
    public Map.Entry<String, String> next() {
      if (!this.hasNext()) {
        throw new NoSuchElementException();
      }
      return this.buffer.poll();
    }

    private void fill() {
      try {
        String line;
        while (null != (line = this.reader.readLine())) {
          if (!this.current.isEmpty()
            && endsWithUnescapedBackslash(this.current)) {
            this.current.setLength(this.current.length() - 1);
            line = line.stripLeading();
          }
          this.current.append(line);
          if (endsWithUnescapedBackslash(this.current)) {
            continue;
          }
          final String logical = this.current.toString();
          this.current.setLength(0);
          final int keyEnd = findKeyEnd(logical, this.options);
          if (-1 == keyEnd) {
            continue;
          }
          final String key = unescapeKeySegment(logical, keyEnd);
          final String value =
            unescapeValue(logical, keyEnd, this.options);
          this.buffer.add(Map.entry(key, value));
          return;
        }
        if (0 < this.current.length()) {
          if (endsWithUnescapedBackslash(this.current)) {
            this.current.append('\\');
          }
          final String logical = this.current.toString();
          this.current.setLength(0);
          final int keyEnd = findKeyEnd(logical, this.options);
          if (-1 != keyEnd) {
            this.buffer.add(Map.entry(
              unescapeKeySegment(logical, keyEnd),
              unescapeValue(logical, keyEnd, this.options)));
          }
        }
        this.finished = true;
      } catch (final IOException exception) {
        throw new UncheckedIOException(exception);
      }
    }
  }
}