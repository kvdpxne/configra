package me.kvdpxne.properties;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import me.kvdpxne.properties.exceptions.PropertyConcurrencyException;
import me.kvdpxne.properties.exceptions.PropertyInterpolationException;
import me.kvdpxne.properties.exceptions.PropertyMissingException;
import me.kvdpxne.properties.exceptions.PropertyWriteException;

/**
 * Immutable, format-agnostic representation of a config file. Supports
 * {@code .properties} and {@code .ini} syntaxes via {@link ConfigFormat}.
 *
 * <h2>Compatibility notes</h2>
 * <ul>
 *   <li><b>Encoding.</b> UTF-8 with ISO-8859-1 fallback for {@code load}.
 *       Streaming APIs use lenient UTF-8.</li>
 *   <li><b>Line terminators.</b> {@code \r\n}, {@code \r} and {@code \n}
 *       all accepted.</li>
 *   <li><b>Trailing whitespace.</b> Preserved by default; opt in with
 *       {@link ParseOptions#trimTrailing()}.</li>
 *   <li><b>Delimiter whitespace.</b> Strict by default.</li>
 *   <li><b>Trailing backslash at EOF.</b> Literal character.</li>
 *   <li><b>{@code \uHHHH} and {@code \xHHHH} escapes.</b> Malformed hex
 *       raises {@link me.kvdpxne.properties.exceptions.PropertyParseException}.</li>
 *   <li><b>Atomic save.</b> Default writes to a sibling temp file and
 *       atomically moves into place.</li>
 * </ul>
 *
 * <h2>Disabled properties</h2>
 * <p>{@link #disable} replaces an active key with a
 * {@link DisabledPropertyNode} that preserves the original value and
 * comment marker. {@link #enable} restores it. Disabled keys are excluded
 * from {@link #find} and {@link #require}, but remain visible through
 * {@link #disabledKeys}.</p>
 *
 * <h2>Threading</h2>
 * <p>Instances are immutable and safe to share across threads. A
 * per-instance cache holds decoded values, populated lazily; hot reads of
 * the same key cost a single {@link ConcurrentHashMap#get} after the first
 * decode.</p>
 */
public final class ConfigFile implements PropertySource {

  private static final int READ_BUFFER = 4096;
  private static final Pattern INTERPOLATION =
    Pattern.compile("\\$\\{([^}]+)}");
  private static final String INTERPOLATION_MARKER = "${";
  private static final Object NULL_SENTINEL = new Object();

  private final List<Node> nodes;
  private final Map<String, String> values;
  private final Path source;
  private final ConfigFormat format;
  private final ParseOptions parseOptions;
  private final boolean caseInsensitiveLookup;

  private final ConcurrentHashMap<PropertyKey<?>, Object> decodeCache =
    new ConcurrentHashMap<>(8, 0.75f, 1);

  ConfigFile(final List<Node> nodes, final Path source,
             final ConfigFormat format, final ParseOptions parseOptions) {
    this.nodes = List.copyOf(nodes);
    this.format = Objects.requireNonNull(format, "format");
    this.parseOptions = Objects.requireNonNull(parseOptions, "parseOptions");
    this.caseInsensitiveLookup = parseOptions.caseInsensitiveKeys()
      || parseOptions.caseInsensitiveSections();

    final Map<String, String> indexed = new LinkedHashMap<>(nodes.size());
    final boolean firstWins =
      parseOptions.duplicatePolicy() == DuplicatePolicy.FIRST_WINS;
    for (final Node node : nodes) {
      if (node instanceof PropertyNode property) {
        final String key = this.lookupKey(property.qualifiedKey());
        if (firstWins && indexed.containsKey(key)) {
          continue;
        }
        indexed.put(key, property.value());
      }
      // DisabledPropertyNode is intentionally excluded from the value
      // map so find/require never see disabled keys.
    }
    this.values = Collections.unmodifiableMap(indexed);
    this.source = source;
  }

  // ----- Loading -----

  public static ConfigFile load(final Path source) throws IOException {
    Objects.requireNonNull(source, "source");
    return load(source, ConfigFormat.fromFileName(
      source.getFileName().toString()));
  }

  public static ConfigFile load(final Path source, final ConfigFormat format)
    throws IOException {
    return load(source, format, defaultParseOptions(format));
  }

  public static ConfigFile load(final Path source,
                                final ConfigFormat format,
                                final ParseOptions options) throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(options, "options");
    if (!Files.isRegularFile(source)) {
      throw new IOException("Not a regular file: " + source);
    }
    final byte[] bytes = Files.readAllBytes(source);
    final String text = decodeLenient(bytes);
    return new ConfigFile(
      format.parse(text, options), source, format, options);
  }

  public static ConfigFile load(final Path source,
                                final Charset charset,
                                final ConfigFormat format,
                                final ParseOptions options)
    throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(charset, "charset");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(options, "options");
    if (!Files.isRegularFile(source)) {
      throw new IOException("Not a regular file: " + source);
    }
    final byte[] bytes = Files.readAllBytes(source);
    final String text = StandardCharsets.UTF_8.equals(charset)
      ? decodeLenient(bytes)
      : new String(bytes, charset);
    return new ConfigFile(
      format.parse(text, options), source, format, options);
  }

  public static ConfigFile load(final InputStream input,
                                final ConfigFormat format)
    throws IOException {
    return load(input, StandardCharsets.UTF_8, format,
      defaultParseOptions(format));
  }

  public static ConfigFile load(final InputStream input,
                                final Charset charset,
                                final ConfigFormat format,
                                final ParseOptions options)
    throws IOException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(charset, "charset");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(options, "options");
    try (Reader reader = new InputStreamReader(input, charset)) {
      return new ConfigFile(
        format.parse(readAll(reader), options),
        null, format, options);
    }
  }

  public static ConfigFile loadFromClasspath(final String resource)
    throws IOException {
    return loadFromClasspath(resource,
      ConfigFormat.fromFileName(resource));
  }

  public static ConfigFile loadFromClasspath(final String resource,
                                             final ConfigFormat format)
    throws IOException {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(format, "format");
    final ClassLoader loader =
      Thread.currentThread().getContextClassLoader();
    try (InputStream in = loader.getResourceAsStream(resource)) {
      if (null == in) {
        throw new IOException("Resource not found: " + resource);
      }
      return load(in, format);
    }
  }

  public static ConfigFile loadOrCreate(final Path source,
                                        final ConfigFile defaults)
    throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(defaults, "defaults");
    if (!Files.exists(source)) {
      defaults.save(source);
    }
    return load(source, defaults.format, defaults.parseOptions);
  }

  public static ConfigFile loadOverlay(final Path base,
                                       final Path... overlays)
    throws IOException {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(overlays, "overlays");
    ConfigFile result = load(base);
    for (final Path overlay : overlays) {
      Objects.requireNonNull(overlay, "overlay");
      if (Files.isRegularFile(overlay)) {
        result = result.overlay(load(overlay));
      }
    }
    return result;
  }

  private static ParseOptions defaultParseOptions(final ConfigFormat format) {
    return ConfigFormat.INI == format
      ? ParseOptions.iniDefaults()
      : ParseOptions.defaults();
  }

  // ----- Lenient decoding -----

  private static String decodeLenient(final byte[] bytes) {
    try {
      final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
      return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    } catch (final CharacterCodingException ignored) {
      return new String(bytes, StandardCharsets.ISO_8859_1);
    }
  }

  private static Reader newLenientUtf8Reader(final Path source)
    throws IOException {
    final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPLACE)
      .onUnmappableCharacter(CodingErrorAction.REPLACE);
    return new InputStreamReader(Files.newInputStream(source), decoder);
  }

  // ----- Async loading -----

  public static CompletableFuture<ConfigFile> loadAsync(final Path source) {
    return loadAsync(source, AsyncSupport.VIRTUAL_EXECUTOR);
  }

  public static CompletableFuture<ConfigFile> loadAsync(
    final Path source, final ExecutorService executor) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(executor, "executor");
    return CompletableFuture.supplyAsync(() -> {
      try {
        return load(source);
      } catch (final IOException exception) {
        throw new CompletionException(exception);
      }
    }, executor);
  }

  public static CompletableFuture<ConfigFile> loadAsync(
    final Path source,
    final ConfigFormat format,
    final ParseOptions options) {
    return loadAsync(source, format, options, AsyncSupport.VIRTUAL_EXECUTOR);
  }

  public static CompletableFuture<ConfigFile> loadAsync(
    final Path source,
    final ConfigFormat format,
    final ParseOptions options,
    final ExecutorService executor) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(executor, "executor");
    return CompletableFuture.supplyAsync(() -> {
      try {
        return load(source, format, options);
      } catch (final IOException exception) {
        throw new CompletionException(exception);
      }
    }, executor);
  }

  public CompletableFuture<ConfigFile> saveAsync(final Path target) {
    return this.saveAsync(target, WriteOptions.utf8(),
      AsyncSupport.VIRTUAL_EXECUTOR);
  }

  public CompletableFuture<ConfigFile> saveAsync(final Path target,
                                                 final WriteOptions options) {
    return this.saveAsync(target, options, AsyncSupport.VIRTUAL_EXECUTOR);
  }

  public CompletableFuture<ConfigFile> saveAsync(final Path target,
                                                 final WriteOptions options,
                                                 final ExecutorService executor) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(executor, "executor");
    return CompletableFuture.supplyAsync(() -> {
      try {
        this.save(target, options);
        return this;
      } catch (final IOException exception) {
        throw new CompletionException(exception);
      }
    }, executor);
  }

  public CompletableFuture<ConfigFile> reloadAsync() {
    return this.reloadAsync(AsyncSupport.VIRTUAL_EXECUTOR);
  }

  public CompletableFuture<ConfigFile> reloadAsync(
    final ExecutorService executor) {
    Objects.requireNonNull(executor, "executor");
    if (null == this.source) {
      return CompletableFuture.failedFuture(new IllegalStateException(
        "Cannot reload a ConfigFile without a source path"));
    }
    return loadAsync(this.source, this.format, this.parseOptions, executor);
  }

  // ----- Streaming -----

  public static Stream<Map.Entry<String, String>> stream(final Path source)
    throws IOException {
    return stream(source, ConfigFormat.fromFileName(
      source.getFileName().toString()));
  }

  public static Stream<Map.Entry<String, String>> stream(
    final Path source, final ConfigFormat format) throws IOException {
    return stream(source, format, defaultParseOptions(format));
  }

  public static Stream<Map.Entry<String, String>> stream(
    final Path source,
    final ConfigFormat format,
    final ParseOptions options) throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(options, "options");
    final Reader reader = newLenientUtf8Reader(source);
    return format.stream(reader, options)
      .onClose(() -> closeQuietly(reader));
  }

  public static Optional<String> peek(final Path source, final String key)
    throws IOException {
    return peek(source,
      ConfigFormat.fromFileName(source.getFileName().toString()),
      key);
  }

  public static Optional<String> peek(final Path source,
                                      final ConfigFormat format,
                                      final String key) throws IOException {
    return peek(source, format, key, defaultParseOptions(format));
  }

  public static Optional<String> peek(final Path source,
                                      final ConfigFormat format,
                                      final String key,
                                      final ParseOptions options)
    throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(options, "options");
    if (!Files.isRegularFile(source)) {
      return Optional.empty();
    }
    try (Reader reader = newLenientUtf8Reader(source)) {
      return format.peek(reader, key, options);
    }
  }

  // W ConfigFile — nowe metody statyczne.

  /**
   * Reads several keys in a single pass. Use this instead of calling
   * {@link #peek(Path, String)} in a loop — the number of file reads is one,
   * regardless of how many keys are requested.
   */
  public static Map<String, String> peekAll(final Path source,
                                            final Collection<String> keys)
    throws IOException {
    Objects.requireNonNull(source, "source");
    final ConfigFormat format = ConfigFormat.fromFileName(
      source.getFileName().toString());
    return peekAll(source, format, keys, defaultParseOptions(format));
  }

  public static Map<String, String> peekAll(final Path source,
                                            final ConfigFormat format,
                                            final Collection<String> keys)
    throws IOException {
    return peekAll(source, format, keys, defaultParseOptions(format));
  }

  public static Map<String, String> peekAll(final Path source,
                                            final ConfigFormat format,
                                            final Collection<String> keys,
                                            final ParseOptions options)
    throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(options, "options");
    if (!Files.isRegularFile(source)) {
      return Map.of();
    }
    try (Reader reader = newLenientUtf8Reader(source)) {
      return format.peekAll(reader, keys, options);
    }
  }

  // ----- Lookup key normalization -----

  private String lookupKey(final String key) {
    return this.caseInsensitiveLookup ? key.toLowerCase(Locale.ROOT) : key;
  }

  // ----- Typed access (with decode cache) -----

  @SuppressWarnings("unchecked")
  public <T> Optional<T> find(final PropertyKey<T> key) {
    Objects.requireNonNull(key, "key");
    final Object cached = this.decodeCache.get(key);
    if (null != cached) {
      return cached == NULL_SENTINEL
        ? Optional.empty()
        : Optional.of((T) cached);
    }
    final String raw = this.values.get(this.lookupKey(key.name()));
    if (null == raw) {
      final T defaultValue = key.defaultValue();
      this.decodeCache.putIfAbsent(key,
        null == defaultValue ? NULL_SENTINEL : defaultValue);
      return Optional.ofNullable(defaultValue);
    }
    final T decoded = key.codec().decode(raw);
    this.decodeCache.putIfAbsent(key, decoded);
    return Optional.of(decoded);
  }

  public <T> T require(final PropertyKey<T> key) {
    Objects.requireNonNull(key, "key");
    return this.find(key).orElseThrow(
      () -> new PropertyMissingException(key.name()));
  }

  public <T> T getOrElse(final PropertyKey<T> key, final T fallback) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(fallback, "fallback");
    return this.find(key).orElse(fallback);
  }

  public boolean contains(final PropertyKey<?> key) {
    Objects.requireNonNull(key, "key");
    return this.values.containsKey(this.lookupKey(key.name()));
  }

  // ----- Typed access via ConfigKeyDeclaration -----

  @SuppressWarnings("unchecked")
  public <T> Optional<T> find(final ConfigKeyDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    return this.find((PropertyKey<T>) declaration.key());
  }

  @SuppressWarnings("unchecked")
  public <T> T require(final ConfigKeyDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    return this.require((PropertyKey<T>) declaration.key());
  }

  @SuppressWarnings("unchecked")
  public <T> T getOrElse(final ConfigKeyDeclaration declaration,
                         final T fallback) {
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(fallback, "fallback");
    return this.getOrElse((PropertyKey<T>) declaration.key(), fallback);
  }

  public boolean contains(final ConfigKeyDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    return this.values.containsKey(
      this.lookupKey(declaration.key().name()));
  }

  // ----- PropertySource -----

  @Override
  public Optional<String> find(final String name) {
    Objects.requireNonNull(name, "name");
    return Optional.ofNullable(this.values.get(this.lookupKey(name)));
  }

  public boolean contains(final String name) {
    Objects.requireNonNull(name, "name");
    return this.values.containsKey(this.lookupKey(name));
  }

  public Set<String> keys() {
    return this.values.keySet();
  }

  public Map<String, String> toRawMap() {
    return this.values;
  }

  public ConfigFormat format() {
    return this.format;
  }

  public ParseOptions parseOptions() {
    return this.parseOptions;
  }

  public Path source() {
    return this.source;
  }

  // ----- Streams over parsed state -----

  public Stream<Map.Entry<String, String>> entries() {
    return this.values.entrySet().stream();
  }

  public <T> Stream<Map.Entry<String, T>> entries(
    final PropertyCodec<T> codec) {
    Objects.requireNonNull(codec, "codec");
    return this.values.entrySet().stream().map(entry ->
      Map.entry(entry.getKey(), codec.decode(entry.getValue())));
  }

  // ----- Diff -----

  public ConfigDiff diff(final ConfigFile other) {
    Objects.requireNonNull(other, "other");
    final Map<String, String> added = new LinkedHashMap<>();
    final Map<String, String> removed = new LinkedHashMap<>();
    final Map<String, String> changed = new LinkedHashMap<>();
    final Map<String, String> previous = new LinkedHashMap<>();

    for (final Map.Entry<String, String> entry : other.values.entrySet()) {
      final String key = entry.getKey();
      final String newValue = entry.getValue();
      final String oldValue = this.values.get(key);
      if (null == oldValue) {
        added.put(key, newValue);
      } else if (!oldValue.equals(newValue)) {
        changed.put(key, newValue);
        previous.put(key, oldValue);
      }
    }
    for (final Map.Entry<String, String> entry : this.values.entrySet()) {
      if (!other.values.containsKey(entry.getKey())) {
        removed.put(entry.getKey(), entry.getValue());
      }
    }
    return new ConfigDiff(added, removed, changed, previous);
  }

  // ----- Immutable mutation via PropertyKey -----

  public <T> ConfigFile with(final PropertyKey<T> key, final T value) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    return this.replaceOrAppend(key.name(), key.codec().encode(value));
  }

  public ConfigFile without(final PropertyKey<?> key) {
    Objects.requireNonNull(key, "key");
    return this.removeKey(key.name());
  }

  public ConfigFile withComment(final PropertyKey<?> key,
                                final String comment) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(comment, "comment");
    return this.withCommentInternal(key.name(), comment);
  }

  // ----- Immutable mutation via ConfigKeyDeclaration -----

  @SuppressWarnings("unchecked")
  public <T> ConfigFile with(final ConfigKeyDeclaration declaration,
                             final T value) {
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(value, "value");
    return this.with((PropertyKey<T>) declaration.key(), value);
  }

  public ConfigFile without(final ConfigKeyDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    return this.without(declaration.key());
  }

  public ConfigFile withComment(final ConfigKeyDeclaration declaration,
                                final String comment) {
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(comment, "comment");
    return this.withCommentInternal(declaration.key().name(), comment);
  }

  // ----- Enable / disable -----

  /**
   * Comments out an active key, preserving its original value in place so
   * {@link #enable(PropertyKey)} can restore it. If the key is not active
   * or is already disabled, {@code this} is returned unchanged.
   *
   * <p>The comment marker used is the format's primary marker: {@code '#'}
   * for {@code .properties}, {@code ';'} for INI.</p>
   */
  public <T> ConfigFile disable(final PropertyKey<T> key) {
    Objects.requireNonNull(key, "key");
    return this.disableInternal(key.name());
  }

  public ConfigFile disable(final ConfigKeyDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    return this.disableInternal(declaration.key().name());
  }

  private ConfigFile disableInternal(final String qualifiedKey) {
    final String normalizedKey = this.lookupKey(qualifiedKey);
    final List<Node> updated = new ArrayList<>(this.nodes.size());
    boolean changed = false;
    final char marker = this.writeMarker();
    for (final Node node : this.nodes) {
      if (node instanceof PropertyNode property
        && this.lookupKey(property.qualifiedKey())
        .equals(normalizedKey)) {
        updated.add(new DisabledPropertyNode(
          property.section(), property.key(), property.value(),
          marker, true));
        changed = true;
      } else {
        updated.add(node);
      }
    }
    return changed
      ? new ConfigFile(updated, this.source, this.format,
      this.parseOptions)
      : this;
  }

  /**
   * Restores a key previously disabled by {@link #disable}. If the key is
   * not present as a {@link DisabledPropertyNode}, {@code this} is
   * returned unchanged.
   */
  public <T> ConfigFile enable(final PropertyKey<T> key) {
    Objects.requireNonNull(key, "key");
    return this.enableInternal(key.name());
  }

  public ConfigFile enable(final ConfigKeyDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    return this.enableInternal(declaration.key().name());
  }

  private ConfigFile enableInternal(final String qualifiedKey) {
    final String normalizedKey = this.lookupKey(qualifiedKey);
    final List<Node> updated = new ArrayList<>(this.nodes.size());
    boolean changed = false;
    for (final Node node : this.nodes) {
      if (node instanceof DisabledPropertyNode disabled
        && this.lookupKey(disabled.qualifiedKey())
        .equals(normalizedKey)) {
        updated.add(disabled.enabled());
        changed = true;
      } else {
        updated.add(node);
      }
    }
    return changed
      ? new ConfigFile(updated, this.source, this.format,
      this.parseOptions)
      : this;
  }

  /**
   * Returns the qualified keys of all disabled pairs, in file order.
   */
  public Set<String> disabledKeys() {
    final Set<String> result = new LinkedHashSet<>();
    for (final Node node : this.nodes) {
      if (node instanceof DisabledPropertyNode disabled) {
        result.add(disabled.qualifiedKey());
      }
    }
    return Collections.unmodifiableSet(result);
  }

  public boolean isDisabled(final PropertyKey<?> key) {
    Objects.requireNonNull(key, "key");
    return this.isDisabledInternal(key.name());
  }

  public boolean isDisabled(final ConfigKeyDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    return this.isDisabledInternal(declaration.key().name());
  }

  private boolean isDisabledInternal(final String qualifiedKey) {
    final String normalizedKey = this.lookupKey(qualifiedKey);
    for (final Node node : this.nodes) {
      if (node instanceof DisabledPropertyNode disabled
        && this.lookupKey(disabled.qualifiedKey())
        .equals(normalizedKey)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the comment marker that {@link #disable} should emit. Chosen
   * by format so it matches the file's prevailing convention.
   */
  private char writeMarker() {
    return ConfigFormat.INI == this.format ? ';' : '#';
  }

  // ----- Batch mutation -----

  public ConfigFile withAll(final Map<PropertyKey<?>, ?> updates) {
    Objects.requireNonNull(updates, "updates");
    if (updates.isEmpty()) {
      return this;
    }
    final Map<String, String> encoded = new LinkedHashMap<>(updates.size());
    for (final Map.Entry<PropertyKey<?>, ?> entry : updates.entrySet()) {
      final PropertyKey<?> key = Objects.requireNonNull(
        entry.getKey(), "key");
      final Object value = Objects.requireNonNull(
        entry.getValue(), "value");
      encoded.put(this.lookupKey(key.name()),
        encodeUntyped(key, value));
    }
    return this.withAllRaw(encoded);
  }

  private ConfigFile withAllRaw(final Map<String, String> encoded) {
    if (encoded.isEmpty()) {
      return this;
    }
    final List<Node> updated =
      new ArrayList<>(this.nodes.size() + encoded.size());
    final Set<String> pending = new HashSet<>(encoded.keySet());
    for (final Node node : this.nodes) {
      if (node instanceof PropertyNode property) {
        final String qualified =
          this.lookupKey(property.qualifiedKey());
        final String replacement = encoded.get(qualified);
        if (null != replacement) {
          if (pending.remove(qualified)) {
            updated.add(this.splitQualified(
              property.qualifiedKey(), replacement));
          }
          continue;
        }
      }
      updated.add(node);
    }
    if (!pending.isEmpty()) {
      if (!updated.isEmpty()
        && !(updated.get(updated.size() - 1) instanceof BlankNode)) {
        updated.add(BlankNode.INSTANCE);
      }
      for (final Map.Entry<String, String> entry : encoded.entrySet()) {
        if (pending.contains(entry.getKey())) {
          updated.add(this.splitQualified(
            entry.getKey(), entry.getValue()));
        }
      }
    }
    return new ConfigFile(updated, this.source, this.format,
      this.parseOptions);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static String encodeUntyped(final PropertyKey key,
                                      final Object value) {
    return key.codec().encode(value);
  }

  // ----- Overlay & interpolation -----

  public ConfigFile overlay(final ConfigFile overrides) {
    Objects.requireNonNull(overrides, "overrides");
    return this.withAllRaw(overrides.values);
  }

  public ConfigFile withInterpolation() {
    final Map<String, String> resolved = new LinkedHashMap<>();
    final Set<String> visiting = new HashSet<>();
    for (final String key : this.values.keySet()) {
      this.resolveValue(key, resolved, visiting);
    }
    final List<Node> updated = new ArrayList<>(this.nodes.size());
    for (final Node node : this.nodes) {
      if (node instanceof PropertyNode property) {
        updated.add(new PropertyNode(property.section(),
          property.key(),
          resolved.get(this.lookupKey(
            property.qualifiedKey()))));
      } else {
        updated.add(node);
      }
    }
    return new ConfigFile(updated, this.source, this.format,
      this.parseOptions);
  }

  // ----- Persistence -----

  public void save() throws IOException {
    if (null == this.source) {
      throw new IllegalStateException(
        "Cannot save a ConfigFile without a source path");
    }
    this.save(this.source);
  }

  public void save(final Path target) throws IOException {
    this.save(target, this.defaultWriteOptions());
  }

  public void save(final Path target, final WriteOptions options)
    throws IOException {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(options, "options");
    if (options.strictThread()) {
      this.assertNotInternalThread(target);
    }
    final Path parent = target.getParent();
    if (null != parent) {
      Files.createDirectories(parent);
    }
    final String text;
    try {
      text = this.format.write(this.nodes, options);
    } catch (final RuntimeException exception) {
      throw new PropertyWriteException(
        target.toString(),
        "Failed to serialise configuration",
        exception);
    }
    if (options.atomic()) {
      this.saveAtomic(target, parent, text, options);
    } else {
      this.saveDirect(target, text, options);
    }
  }

  private WriteOptions defaultWriteOptions() {
    return ConfigFormat.INI == this.format
      ? WriteOptions.ini()
      : WriteOptions.utf8();
  }

  private void saveAtomic(final Path target, final Path parent,
                          final String text, final WriteOptions options)
    throws IOException {
    final Path dir = null != parent
      ? parent
      : target.toAbsolutePath().getParent();
    final Path temp = Files.createTempFile(dir, ".properties-", ".tmp");
    try {
      try (Writer writer =
             Files.newBufferedWriter(temp, options.charset())) {
        writer.write(text);
      }
      try {
        Files.move(temp, target,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      } catch (final AtomicMoveNotSupportedException ignored) {
        Files.move(temp, target,
          StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private void saveDirect(final Path target, final String text,
                          final WriteOptions options) throws IOException {
    try (Writer writer =
           Files.newBufferedWriter(target, options.charset())) {
      writer.write(text);
    }
  }

  private void assertNotInternalThread(final Path target) {
    final Thread current = Thread.currentThread();
    final String name = current.getName();
    if (AsyncSupport.isInternalThreadName(name)) {
      throw new PropertyConcurrencyException(
        "main or caller thread",
        name,
        "save() called from internal thread '" + name
          + "' for target " + target
          + ". Use saveAsync() or schedule on the main thread.");
    }
  }

  public ConfigFile reload() throws IOException {
    if (null == this.source) {
      throw new IllegalStateException(
        "Cannot reload a ConfigFile without a source path");
    }
    return load(this.source, this.format, this.parseOptions);
  }

  // ----- Object -----

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ConfigFile that)) {
      return false;
    }
    return this.values.equals(that.values);
  }

  @Override
  public int hashCode() {
    return this.values.hashCode();
  }

  @Override
  public String toString() {
    return "ConfigFile{keys=" + this.values.size()
      + ", format=" + this.format + ", source=" + this.source + '}';
  }

  // ----- Internals -----

  private ConfigFile replaceOrAppend(final String qualifiedKey,
                                     final String encodedValue) {
    final String normalizedKey = this.lookupKey(qualifiedKey);
    final List<Node> updated = new ArrayList<>(this.nodes.size() + 1);
    boolean replaced = false;
    for (final Node node : this.nodes) {
      if (node instanceof PropertyNode property
        && this.lookupKey(property.qualifiedKey())
        .equals(normalizedKey)) {
        if (!replaced) {
          updated.add(this.splitQualified(
            property.qualifiedKey(), encodedValue));
          replaced = true;
        }
      } else {
        updated.add(node);
      }
    }
    if (!replaced) {
      if (!updated.isEmpty()
        && !(updated.get(updated.size() - 1) instanceof BlankNode)) {
        updated.add(BlankNode.INSTANCE);
      }
      updated.add(this.splitQualified(qualifiedKey, encodedValue));
    }
    return new ConfigFile(updated, this.source, this.format,
      this.parseOptions);
  }

  private ConfigFile withCommentInternal(final String qualifiedKey,
                                         final String comment) {
    final String normalizedKey = this.lookupKey(qualifiedKey);
    final String text = comment.startsWith("#") || comment.startsWith("!")
      || comment.startsWith(";")
      ? comment
      : "# " + comment;

    final List<Node> updated = new ArrayList<>(this.nodes.size() + 1);
    boolean inserted = false;
    for (final Node node : this.nodes) {
      final boolean match = (node instanceof PropertyNode property
        && this.lookupKey(property.qualifiedKey())
        .equals(normalizedKey))
        || (node instanceof DisabledPropertyNode disabled
        && this.lookupKey(disabled.qualifiedKey())
        .equals(normalizedKey));
      if (!inserted && match) {
        updated.add(new CommentNode(text));
        updated.add(node);
        inserted = true;
      } else {
        updated.add(node);
      }
    }
    if (!inserted) {
      if (!updated.isEmpty()
        && !(updated.get(updated.size() - 1) instanceof BlankNode)) {
        updated.add(BlankNode.INSTANCE);
      }
      updated.add(new CommentNode(text));
    }
    return new ConfigFile(updated, this.source, this.format,
      this.parseOptions);
  }

  private PropertyNode splitQualified(final String qualifiedKey,
                                      final String value) {
    if (ConfigFormat.INI == this.format && qualifiedKey.contains(".")) {
      final int lastDot = qualifiedKey.lastIndexOf('.');
      return new PropertyNode(
        qualifiedKey.substring(0, lastDot),
        qualifiedKey.substring(lastDot + 1),
        value);
    }
    return new PropertyNode(null, qualifiedKey, value);
  }

  private ConfigFile removeKey(final String qualifiedKey) {
    final String normalizedKey = this.lookupKey(qualifiedKey);
    final List<Node> updated = new ArrayList<>(this.nodes.size());
    boolean removed = false;
    for (final Node node : this.nodes) {
      if (node instanceof PropertyNode property
        && this.lookupKey(property.qualifiedKey())
        .equals(normalizedKey)) {
        removed = true;
        continue;
      }
      if (node instanceof DisabledPropertyNode disabled
        && this.lookupKey(disabled.qualifiedKey())
        .equals(normalizedKey)) {
        removed = true;
        continue;
      }
      updated.add(node);
    }
    return removed
      ? new ConfigFile(updated, this.source, this.format,
      this.parseOptions)
      : this;
  }

  private String resolveValue(final String key,
                              final Map<String, String> resolved,
                              final Set<String> visiting) {
    final String cached = resolved.get(key);
    if (null != cached) {
      return cached;
    }
    if (!visiting.add(key)) {
      throw new PropertyInterpolationException(
        key, key,
        "Circular interpolation reference: " + key);
    }
    final String raw = this.values.get(key);
    if (null == raw) {
      throw new PropertyInterpolationException(
        key, key,
        "Missing property referenced by interpolation: " + key);
    }
    if (-1 == raw.indexOf(INTERPOLATION_MARKER)) {
      visiting.remove(key);
      resolved.put(key, raw);
      return raw;
    }
    final Matcher matcher = INTERPOLATION.matcher(raw);
    final StringBuilder out = new StringBuilder(raw.length());
    int last = 0;
    while (matcher.find()) {
      out.append(raw, last, matcher.start());
      final String refName = this.lookupKey(
        matcher.group(1).trim());
      if (!this.values.containsKey(refName)) {
        throw new PropertyInterpolationException(
          key, refName,
          "Missing property '" + refName
            + "' referenced from '" + key + '\'');
      }
      out.append(this.resolveValue(refName, resolved, visiting));
      last = matcher.end();
    }
    out.append(raw, last, raw.length());
    final String result = out.toString();
    visiting.remove(key);
    resolved.put(key, result);
    return result;
  }

  private static String readAll(final Reader reader) throws IOException {
    final StringBuilder out = new StringBuilder(READ_BUFFER);
    final char[] buffer = new char[READ_BUFFER];
    int read;
    while (-1 != (read = reader.read(buffer))) {
      out.append(buffer, 0, read);
    }
    return out.toString();
  }

  private static void closeQuietly(final Reader reader) {
    try {
      reader.close();
    } catch (final IOException ignored) {
      // Read-only close, nothing actionable.
    }
  }
}