package me.kvd.properties.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import me.kvd.properties.ConfigKeyDeclaration;
import me.kvd.properties.DuplicatePolicy;
import me.kvd.properties.PropertyCodec;
import me.kvd.properties.PropertyKey;
import me.kvd.properties.PropertySource;
import me.kvd.properties.diff.ConfigDiff;
import me.kvd.properties.exceptions.PropertyMissingException;
import me.kvd.properties.exceptions.PropertyTypeException;
import me.kvd.properties.options.ParseOptions;
import me.kvd.properties.options.WriteOptions;
import me.kvd.properties.spi.ConfigFormat;
import me.kvd.properties.spi.node.BlankNode;
import me.kvd.properties.spi.node.CommentNode;
import me.kvd.properties.spi.node.DisabledPropertyNode;
import me.kvd.properties.spi.node.Node;
import me.kvd.properties.spi.node.PropertyNode;
import me.kvd.properties.util.AsyncSupport;
import me.kvd.properties.util.Charsets;

/**
 * Immutable, format-agnostic representation of a config file.
 *
 * <h2>Responsibility</h2>
 * <p>This class owns the parsed node graph and the value index. It exposes
 * typed access, immutable mutation and equality, and delegates I/O to
 * {@link ConfigFileIO}, interpolation to {@link ConfigInterpolation}, and
 * charset handling to {@link Charsets}.</p>
 *
 * <h2>Equality</h2>
 * <p>{@link #equals(Object)} compares the value map only; {@link
 * #contentEquals(Object)} compares the full node graph.</p>
 *
 * <h2>Threading</h2>
 * <p>Instances are immutable and safe to share across threads. A
 * per-instance cache holds decoded values, populated lazily.</p>
 */
public final class ConfigFile implements PropertySource {

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
    this.caseInsensitiveLookup = parseOptions.caseOptions().insensitiveKeys()
      || parseOptions.caseOptions().insensitiveSections();

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
    }
    this.values = Collections.unmodifiableMap(indexed);
    this.source = source;
  }

  // ----- Package-private accessors for ConfigFileIO -----

  List<Node> nodes() {
    return this.nodes;
  }

  Map<String, String> values() {
    return this.values;
  }

  char writeMarker() {
    return this.parseOptions.comments().chars().primary();
  }

  // ----- Factories -----

  /**
   * Returns an empty instance with no nodes.
   */
  public static ConfigFile empty(final ConfigFormat format) {
    Objects.requireNonNull(format, "format");
    return new ConfigFile(List.of(), null, format,
      ConfigFileIO.defaultParseOptions(format));
  }

  /**
   * Returns a new instance whose node graph reflects {@code values} in
   * iteration order. For INI, dotted keys are split into section and
   * local name ({@code server.port} → section {@code server}, key
   * {@code port}). For properties, keys are stored verbatim.
   */
  public static ConfigFile of(final Map<String, String> values,
                              final ConfigFormat format) {
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(format, "format");
    if (values.isEmpty()) {
      return empty(format);
    }
    final boolean ini = ConfigFormats.isIni(format);
    final List<Node> nodes = new ArrayList<>(values.size());
    for (final Map.Entry<String, String> entry : values.entrySet()) {
      final String key = Objects.requireNonNull(entry.getKey(), "key");
      final String value = Objects.requireNonNull(entry.getValue(), "value");
      validateKey(key);
      if (ini) {
        final int dot = key.lastIndexOf('.');
        if (0 < dot && dot < key.length() - 1) {
          nodes.add(new PropertyNode(
            key.substring(0, dot),
            key.substring(dot + 1),
            value));
          continue;
        }
      }
      nodes.add(new PropertyNode(null, key, value));
    }
    return new ConfigFile(nodes, null, format,
      ConfigFileIO.defaultParseOptions(format));
  }

  private static void validateKey(final String key) {
    if (key.isEmpty()) {
      throw new IllegalArgumentException("Key must not be empty");
    }
    if (!key.equals(key.strip())) {
      throw new IllegalArgumentException(
        "Key must not have leading or trailing whitespace: '" + key + '\'');
    }
    for (int i = 0; i < key.length(); i++) {
      if (Character.isISOControl(key.charAt(i))) {
        throw new IllegalArgumentException(
          "Key must not contain ISO control characters: '" + key + '\'');
      }
    }
  }

  // ----- Static delegation to ConfigFileIO -----

  public static ConfigFile load(final Path source) throws IOException {
    return ConfigFileIO.load(source);
  }

  public static ConfigFile load(final Path source, final ConfigFormat format)
    throws IOException {
    return ConfigFileIO.load(source, format);
  }

  public static ConfigFile load(final Path source,
                                final ConfigFormat format,
                                final ParseOptions options) throws IOException {
    return ConfigFileIO.load(source, format, options);
  }

  public static ConfigFile load(final Path source,
                                final Charset charset,
                                final ConfigFormat format,
                                final ParseOptions options)
    throws IOException {
    return ConfigFileIO.load(source, charset, format, options);
  }

  public static ConfigFile load(final InputStream input,
                                final ConfigFormat format)
    throws IOException {
    return ConfigFileIO.load(input, format);
  }

  public static ConfigFile load(final InputStream input,
                                final Charset charset,
                                final ConfigFormat format,
                                final ParseOptions options)
    throws IOException {
    return ConfigFileIO.load(input, charset, format, options);
  }

  public static ConfigFile loadFromClasspath(final String resource)
    throws IOException {
    return ConfigFileIO.loadFromClasspath(resource);
  }

  public static ConfigFile loadFromClasspath(final String resource,
                                             final ConfigFormat format)
    throws IOException {
    return ConfigFileIO.loadFromClasspath(resource, format);
  }

  public static ConfigFile loadOrCreate(final Path source,
                                        final ConfigFile defaults)
    throws IOException {
    return ConfigFileIO.loadOrCreate(source, defaults);
  }

  public static ConfigFile loadOverlay(final Path base, final Path... overlays)
    throws IOException {
    return ConfigFileIO.loadOverlay(base, overlays);
  }

  public static CompletableFuture<ConfigFile> loadAsync(final Path source) {
    return ConfigFileIO.loadAsync(source);
  }

  public static CompletableFuture<ConfigFile> loadAsync(
    final Path source, final ExecutorService executor) {
    return ConfigFileIO.loadAsync(source, executor);
  }

  public static CompletableFuture<ConfigFile> loadAsync(
    final Path source, final ConfigFormat format,
    final ParseOptions options) {
    return ConfigFileIO.loadAsync(source, format, options);
  }

  public static CompletableFuture<ConfigFile> loadAsync(
    final Path source, final ConfigFormat format,
    final ParseOptions options, final ExecutorService executor) {
    return ConfigFileIO.loadAsync(source, format, options, executor);
  }

  public static Stream<Map.Entry<String, String>> stream(final Path source)
    throws IOException {
    return ConfigFileIO.stream(source);
  }

  public static Stream<Map.Entry<String, String>> stream(
    final Path source, final ConfigFormat format) throws IOException {
    return ConfigFileIO.stream(source, format);
  }

  public static Stream<Map.Entry<String, String>> stream(
    final Path source, final ConfigFormat format,
    final ParseOptions options) throws IOException {
    return ConfigFileIO.stream(source, format, options);
  }

  public static Optional<String> peek(final Path source, final String key)
    throws IOException {
    return ConfigFileIO.peek(source, key);
  }

  public static Optional<String> peek(final Path source,
                                      final ConfigFormat format,
                                      final String key) throws IOException {
    return ConfigFileIO.peek(source, format, key);
  }

  public static Optional<String> peek(final Path source,
                                      final ConfigFormat format,
                                      final String key,
                                      final ParseOptions options)
    throws IOException {
    return ConfigFileIO.peek(source, format, key, options);
  }

  public static Map<String, String> peekAll(final Path source,
                                            final Collection<String> keys)
    throws IOException {
    return ConfigFileIO.peekAll(source, keys);
  }

  public static Map<String, String> peekAll(final Path source,
                                            final ConfigFormat format,
                                            final Collection<String> keys)
    throws IOException {
    return ConfigFileIO.peekAll(source, format, keys);
  }

  public static Map<String, String> peekAll(final Path source,
                                            final ConfigFormat format,
                                            final Collection<String> keys,
                                            final ParseOptions options)
    throws IOException {
    return ConfigFileIO.peekAll(source, format, keys, options);
  }

  // ----- Lookup key normalization -----

  private String lookupKey(final String key) {
    return this.caseInsensitiveLookup ? key.toLowerCase(Locale.ROOT) : key;
  }

  // find() używa teraz własnego pola:
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

  // ----- Accessors -----

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
    return ConfigDiff.of(this.values, other.values,
      this.disabledKeys(), other.disabledKeys());
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

  public ConfigFile withComment(final PropertyKey<?> key, final String comment) {
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

  public <T> ConfigFile disable(final PropertyKey<T> key) {
    Objects.requireNonNull(key, "key");
    return this.disableInternal(key.name());
  }

  public ConfigFile disable(final ConfigKeyDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    return this.disableInternal(declaration.key().name());
  }

  public <T> ConfigFile enable(final PropertyKey<T> key) {
    Objects.requireNonNull(key, "key");
    return this.enableInternal(key.name());
  }

  public ConfigFile enable(final ConfigKeyDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    return this.enableInternal(declaration.key().name());
  }

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

  // ----- Batch mutation -----

  /**
   * ...
   *
   * <p><b>Type checking.</b> Values are validated against
   * {@link PropertyCodec#valueType()} only when the codec declares a
   * concrete type. Codecs built via {@link PropertyCodec#of(Function, Function)}
   * declare {@code Object.class} and opt out of the check — passing a value
   * of the wrong runtime type will fail during encoding or at the next
   * {@link #require}. Codecs built via {@code PropertyCodec.of(Class, ...)}
   * (all built-ins except the two-arg factory) are fully checked.</p>
   */
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
      validateValueType(key, value);
      encoded.put(this.lookupKey(key.name()), encodeUntyped(key, value));
    }
    return this.withAllRaw(encoded);
  }

  // ----- Overlay & interpolation -----

  public ConfigFile overlay(final ConfigFile overrides) {
    Objects.requireNonNull(overrides, "overrides");
    return this.withAllRaw(overrides.values);
  }

  public ConfigFile withInterpolation() {
    return ConfigInterpolation.apply(this);
  }

  // ----- Persistence (delegation) -----

  public void save() throws IOException {
    if (null == this.source) {
      throw new IllegalStateException(
        "Cannot save a ConfigFile without a source path");
    }
    this.save(this.source);
  }

  public void save(final Path target) throws IOException {
    this.save(target, ConfigFileIO.defaultWriteOptions(this.format));
  }

  public void save(final Path target, final WriteOptions options)
    throws IOException {
    ConfigFileIO.save(this, target, options);
  }

  public CompletableFuture<ConfigFile> saveAsync(final Path target) {
    return ConfigFileIO.saveAsync(this, target,
      ConfigFileIO.defaultWriteOptions(this.format));
  }

  public CompletableFuture<ConfigFile> saveAsync(final Path target,
                                                 final WriteOptions options) {
    return ConfigFileIO.saveAsync(this, target, options);
  }

  public CompletableFuture<ConfigFile> saveAsync(final Path target,
                                                 final WriteOptions options,
                                                 final ExecutorService executor) {
    return ConfigFileIO.saveAsync(this, target, options, executor);
  }

  public ConfigFile reload() throws IOException {
    if (null == this.source) {
      throw new IllegalStateException(
        "Cannot reload a ConfigFile without a source path");
    }
    return ConfigFileIO.load(this.source, this.format, this.parseOptions);
  }

  public CompletableFuture<ConfigFile> reloadAsync() {
    return this.reloadAsync(AsyncSupport.executor());
  }

  public CompletableFuture<ConfigFile> reloadAsync(
    final ExecutorService executor) {
    Objects.requireNonNull(executor, "executor");
    if (null == this.source) {
      return CompletableFuture.failedFuture(new IllegalStateException(
        "Cannot reload a ConfigFile without a source path"));
    }
    return ConfigFileIO.loadAsync(
      this.source, this.format, this.parseOptions, executor);
  }

  // ----- Serialisation without touching the disk -----

  /**
   * Appends the serialised form of this file to {@code target} using
   * {@code options}. Useful for tests, in-memory piping, or embedding the
   * serialised content in another output.
   */
  public void writeTo(final Appendable target, final WriteOptions options)
    throws IOException {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(options, "options");
    target.append(this.format.write(this.nodes, options));
  }

  /**
   * Returns the serialised form of this file using the format's default
   * {@link WriteOptions}. For INI files that means {@link WriteOptions#ini()};
   * for everything else, {@link WriteOptions#utf8()}.
   */
  public String writeToString() {
    return this.format.write(this.nodes, this.defaultWriteOptions());
  }

// ----- Interop -----

  /**
   * Returns a {@link java.util.Properties} populated from this file's value
   * map. Comments, ordering and disabled keys are not preserved — use this
   * only for interop with legacy code that expects a {@code Properties}
   * instance. For the reverse direction, see
   * {@link java.util.Properties#forEach(java.util.function.BiConsumer)}.
   */
  public java.util.Properties toJavaProperties() {
    final java.util.Properties props = new java.util.Properties();
    for (final Map.Entry<String, String> entry : this.values.entrySet()) {
      props.setProperty(entry.getKey(), entry.getValue());
    }
    return props;
  }

// ----- Convenience -----

  /** Returns the number of active (non-disabled) keys. */
  public int size() {
    return this.values.size();
  }

  /**
   * Returns the 1-based line number of {@code key} in the original source,
   * or {@code -1} when the key is absent or the {@code ConfigFile} was built
   * programmatically (no source line info). Both active and disabled nodes
   * are searched; the first match in file order wins.
   *
   * <p>Use this in error messages ("missing key, expected near line X")
   * rather than as a programmatic lookup — for that use
   * {@link #contains(PropertyKey)}.</p>
   */
  public int indexOf(final PropertyKey<?> key) {
    Objects.requireNonNull(key, "key");
    final String target = this.lookupKey(key.name());
    for (final Node node : this.nodes) {
      final String nodeKey;
      final int line;
      if (node instanceof PropertyNode property) {
        nodeKey = this.lookupKey(property.qualifiedKey());
        line = property.line();
      } else if (node instanceof DisabledPropertyNode disabled) {
        nodeKey = this.lookupKey(disabled.qualifiedKey());
        line = disabled.line();
      } else {
        continue;
      }
      if (nodeKey.equals(target)) {
        return 0 <= line ? line + 1 : -1;
      }
    }
    return -1;
  }

  // ----- Equality -----

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

  public boolean contentEquals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ConfigFile that)) {
      return false;
    }
    return this.nodes.equals(that.nodes);
  }

  public int contentHashCode() {
    return this.nodes.hashCode();
  }

  @Override
  public String toString() {
    return "ConfigFile{keys=" + this.values.size()
      + ", format=" + this.format + ", source=" + this.source + '}';
  }

  // ----- Internals -----

  private WriteOptions defaultWriteOptions() {
    return ConfigFormats.isIni(this.format)
      ? WriteOptions.ini()
      : WriteOptions.utf8();
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
          marker, true, property.line()));
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

  private static void validateValueType(final PropertyKey<?> key,
                                        final Object value) {
    final Class<?> expected = key.codec().valueType();
    if (Object.class == expected) {
      return;
    }
    if (!expected.isInstance(value)) {
      throw new PropertyTypeException(
        key.name(),
        String.valueOf(value),
        "Value of type " + value.getClass().getName()
          + " is not assignable to " + expected.getName()
          + " for key '" + key.name() + '\'',
        null);
    }
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
            updated.add(new PropertyNode(
              property.section(), property.key(), replacement,
              property.line()));
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
          updated.add(splitQualified(
            entry.getKey(), entry.getValue()));
        }
      }
    }
    return new ConfigFile(updated, this.source, this.format,
      this.parseOptions);
  }

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
          updated.add(new PropertyNode(
            property.section(), property.key(), encodedValue,
            property.line()));
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
      updated.add(splitQualified(qualifiedKey, encodedValue));
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
    if (ConfigFormats.isIni(this.format) && qualifiedKey.contains(".")) {
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static String encodeUntyped(final PropertyKey key,
                                      final Object value) {
    return key.codec().encode(value);
  }
}