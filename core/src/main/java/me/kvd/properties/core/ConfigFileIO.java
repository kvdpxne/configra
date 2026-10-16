package me.kvd.properties.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import me.kvd.properties.exceptions.PropertyConcurrencyException;
import me.kvd.properties.exceptions.PropertyWriteException;
import me.kvd.properties.options.ParseOptions;
import me.kvd.properties.options.WriteOptions;
import me.kvd.properties.spi.ConfigFormat;
import me.kvd.properties.spi.node.Node;
import me.kvd.properties.util.AsyncSupport;
import me.kvd.properties.util.Charsets;

/**
 * Stateless I/O for {@link ConfigFile}. Handles all file-system access,
 * charset decoding, streaming and atomic persistence. Kept separate from
 * {@code ConfigFile} so that the core data holder has no dependency on
 * {@link java.nio.file.Files}.
 */
public final class ConfigFileIO {

  static final Object NULL_SENTINEL = new Object();
  private static final int READ_BUFFER = 4096;

  private ConfigFileIO() {
    throw new AssertionError("No instances.");
  }

  // ----- Defaults -----

  static ParseOptions defaultParseOptions(final ConfigFormat format) {
    return ConfigFormats.isIni(format)
      ? ParseOptions.iniDefaults()
      : ParseOptions.defaults();
  }

  static WriteOptions defaultWriteOptions(final ConfigFormat format) {
    return ConfigFormats.isIni(format)
      ? WriteOptions.ini()
      : WriteOptions.utf8();
  }

  // ----- Load -----

  public static ConfigFile load(final Path source) throws IOException {
    Objects.requireNonNull(source, "source");
    return load(source, ConfigFormats.requireFromFileName(
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
    final String text = Charsets.decodeLenient(bytes);
    return new ConfigFile(format.parse(text, options), source, format, options);
  }

  public static ConfigFile load(final Path source,
                                final Charset charset,
                                final ConfigFormat format,
                                final ParseOptions options) throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(charset, "charset");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(options, "options");
    if (!Files.isRegularFile(source)) {
      throw new IOException("Not a regular file: " + source);
    }
    final byte[] bytes = Files.readAllBytes(source);
    final String text = StandardCharsets.UTF_8.equals(charset)
      ? Charsets.decodeLenient(bytes)
      : Charsets.decodeWith(bytes, charset);
    return new ConfigFile(format.parse(text, options), source, format, options);
  }

  public static ConfigFile load(final InputStream input,
                                final ConfigFormat format) throws IOException {
    return load(input, StandardCharsets.UTF_8, format,
      defaultParseOptions(format));
  }

  public static ConfigFile load(final InputStream input,
                                final Charset charset,
                                final ConfigFormat format,
                                final ParseOptions options) throws IOException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(charset, "charset");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(options, "options");

    final PushbackInputStream pushback = input instanceof PushbackInputStream p
      ? p
      : new PushbackInputStream(input, 3);
    // Peek 3 bytes; if BOM, consume; otherwise unread.
    final byte[] head = new byte[3];
    int read = 0;
    while (read < 3) {
      final int n = pushback.read(head, read, 3 - read);
      if (-1 == n) {
        break;
      }
      read += n;
    }
    if (3 == read && Charsets.hasUtf8Bom(head)) {
      // BOM consumed; nothing to unread.
    } else if (0 < read) {
      pushback.unread(head, 0, read);
    }

    try (Reader reader = new InputStreamReader(pushback, charset)) {
      return new ConfigFile(
        format.parse(readAll(reader), options),
        null, format, options);
    }
  }

  public static ConfigFile loadFromClasspath(final String resource)
    throws IOException {
    return loadFromClasspath(resource,
      ConfigFormats.requireFromFileName(resource));
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
    return load(source, defaults.format(), defaults.parseOptions());
  }

  public static ConfigFile loadOverlay(final Path base, final Path... overlays)
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

  // ----- Async -----

  public static CompletableFuture<ConfigFile> loadAsync(final Path source) {
    return loadAsync(source, AsyncSupport.executor());
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
    final Path source, final ConfigFormat format,
    final ParseOptions options) {
    return loadAsync(source, format, options, AsyncSupport.executor());
  }

  public static CompletableFuture<ConfigFile> loadAsync(
    final Path source, final ConfigFormat format,
    final ParseOptions options, final ExecutorService executor) {
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

  public static CompletableFuture<ConfigFile> saveAsync(
    final ConfigFile config, final Path target,
    final WriteOptions options) {
    return saveAsync(config, target, options, AsyncSupport.executor());
  }

  public static CompletableFuture<ConfigFile> saveAsync(
    final ConfigFile config, final Path target,
    final WriteOptions options, final ExecutorService executor) {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(executor, "executor");
    return CompletableFuture.supplyAsync(() -> {
      try {
        save(config, target, options);
        return config;
      } catch (final IOException exception) {
        throw new CompletionException(exception);
      }
    }, executor);
  }

  // ----- Stream / peek -----

  public static Stream<Map.Entry<String, String>> stream(final Path source)
    throws IOException {
    return stream(source, ConfigFormats.requireFromFileName(
      source.getFileName().toString()));
  }

  public static Stream<Map.Entry<String, String>> stream(
    final Path source, final ConfigFormat format) throws IOException {
    return stream(source, format, defaultParseOptions(format));
  }

  public static Stream<Map.Entry<String, String>> stream(
    final Path source, final ConfigFormat format,
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
    return peek(source, ConfigFormats.requireFromFileName(
      source.getFileName().toString()), key);
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

  public static Map<String, String> peekAll(final Path source,
                                            final Collection<String> keys)
    throws IOException {
    final ConfigFormat format = ConfigFormats.requireFromFileName(
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

  // ----- Save -----

  static void save(final ConfigFile config, final Path target,
                   final WriteOptions options) throws IOException {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(options, "options");
    if (options.strictThread()) {
      assertNotInternalThread(target);
    }
    final Path parent = target.getParent();
    if (null != parent) {
      Files.createDirectories(parent);
    }
    final List<Node> nodes = config.nodes();
    final String text;
    try {
      text = config.format().write(nodes, options);
    } catch (final RuntimeException exception) {
      throw new PropertyWriteException(
        target.toString(),
        "Failed to serialise configuration",
        exception);
    }
    if (options.atomic()) {
      saveAtomic(target, parent, text, options);
    } else {
      saveDirect(target, text, options);
    }
  }

  private static void saveAtomic(final Path target, final Path parent,
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

  private static void saveDirect(final Path target, final String text,
                                 final WriteOptions options) throws IOException {
    try (Writer writer =
           Files.newBufferedWriter(target, options.charset())) {
      writer.write(text);
    }
  }

  private static void assertNotInternalThread(final Path target) {
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

  // ----- Utilities -----

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

  /**
   * Opens {@code source} with a UTF-8 decoder that replaces malformed input
   * with {@code U+FFFD} instead of throwing. Used by the streaming APIs so
   * they do not diverge from {@link #load(Path)} on decoding errors.
   */
  private static Reader newLenientUtf8Reader(final Path source)
    throws IOException {
    final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPLACE)
      .onUnmappableCharacter(CodingErrorAction.REPLACE);
    return new InputStreamReader(Files.newInputStream(source), decoder);
  }
}