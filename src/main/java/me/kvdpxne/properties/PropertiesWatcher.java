package me.kvdpxne.properties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Polls a config file and invokes a callback whenever its modification time
 * changes. Backed by a daemon thread.
 *
 * <p>When {@code format} and {@code parseOptions} are omitted, the file is
 * loaded via {@link ConfigFile#load(Path)}, which selects the format from
 * the file extension and applies format-appropriate parse options
 * ({@link ParseOptions#iniDefaults()} for {@code .ini}).</p>
 */
public final class PropertiesWatcher implements AutoCloseable {

  static final String THREAD_PREFIX = "config-watcher-";

  private static final long POLL_INTERVAL_MS = 500L;

  private final Path source;
  private final ConfigFormat format;         // nullable → auto-detect
  private final ParseOptions parseOptions;   // nullable → format default
  private final Consumer<ConfigFile> onChange;
  private final Consumer<IOException> onError;
  private final ScheduledExecutorService scheduler;
  private final AtomicLong lastModified = new AtomicLong();
  private final AtomicBoolean closed = new AtomicBoolean();

  private PropertiesWatcher(final Path source,
                            final ConfigFormat format,
                            final ParseOptions parseOptions,
                            final Consumer<ConfigFile> onChange,
                            final Consumer<IOException> onError) {
    this.source = source;
    this.format = format;
    this.parseOptions = parseOptions;
    this.onChange = onChange;
    this.onError = onError;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
      final Thread thread = new Thread(runnable,
        THREAD_PREFIX + source.getFileName());
      thread.setDaemon(true);
      return thread;
    });
    this.lastModified.set(this.readModificationTime());
  }

  // ----- Factories: default (auto-detect) -----

  public static PropertiesWatcher watch(final Path source,
                                        final Consumer<ConfigFile> onChange)
    throws IOException {
    return watch(source, null, null, onChange, silentErrorHandler());
  }

  public static PropertiesWatcher watch(final Path source,
                                        final Consumer<ConfigFile> onChange,
                                        final Consumer<IOException> onError)
    throws IOException {
    return watch(source, null, null, onChange, onError);
  }

  // ----- Factories: explicit parse options -----

  public static PropertiesWatcher watch(final Path source,
                                        final ParseOptions parseOptions,
                                        final Consumer<ConfigFile> onChange)
    throws IOException {
    return watch(source, null, parseOptions, onChange,
      silentErrorHandler());
  }

  public static PropertiesWatcher watch(final Path source,
                                        final ParseOptions parseOptions,
                                        final Consumer<ConfigFile> onChange,
                                        final Consumer<IOException> onError)
    throws IOException {
    return watch(source, null, parseOptions, onChange, onError);
  }

  // ----- Factories: explicit format + options -----

  public static PropertiesWatcher watch(final Path source,
                                        final ConfigFormat format,
                                        final ParseOptions parseOptions,
                                        final Consumer<ConfigFile> onChange)
    throws IOException {
    return watch(source, format, parseOptions, onChange,
      silentErrorHandler());
  }

  public static PropertiesWatcher watch(final Path source,
                                        final ConfigFormat format,
                                        final ParseOptions parseOptions,
                                        final Consumer<ConfigFile> onChange,
                                        final Consumer<IOException> onError)
    throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(onChange, "onChange");
    Objects.requireNonNull(onError, "onError");
    if (!Files.isRegularFile(source)) {
      throw new IOException("Not a regular file: " + source);
    }
    final PropertiesWatcher watcher = new PropertiesWatcher(
      source, format, parseOptions, onChange, onError);
    watcher.scheduler.scheduleWithFixedDelay(
      watcher::tick,
      POLL_INTERVAL_MS,
      POLL_INTERVAL_MS,
      TimeUnit.MILLISECONDS);
    return watcher;
  }

  // ----- Diff-based factories -----

  public static PropertiesWatcher watchDiff(final Path source,
                                            final Consumer<ConfigDiff> onChange)
    throws IOException {
    return watchDiff(source, null, null, onChange);
  }

  public static PropertiesWatcher watchDiff(final Path source,
                                            final ParseOptions parseOptions,
                                            final Consumer<ConfigDiff> onChange)
    throws IOException {
    return watchDiff(source, null, parseOptions, onChange);
  }

  public static PropertiesWatcher watchDiff(final Path source,
                                            final ConfigFormat format,
                                            final ParseOptions parseOptions,
                                            final Consumer<ConfigDiff> onChange)
    throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(onChange, "onChange");
    final AtomicReference<ConfigFile> previous = new AtomicReference<>(
      loadInitial(source, format, parseOptions));
    return watch(source, format, parseOptions, updated -> {
      final ConfigFile old = previous.getAndSet(updated);
      if (null == old) {
        return;
      }
      final ConfigDiff diff = old.diff(updated);
      if (!diff.isEmpty()) {
        onChange.accept(diff);
      }
    });
  }

  // ----- Internal -----

  private static Consumer<IOException> silentErrorHandler() {
    return ignored -> {
      // Silent by default; callers supply a handler when they care.
    };
  }

  private static ConfigFile loadInitial(final Path source,
                                        final ConfigFormat format,
                                        final ParseOptions parseOptions)
    throws IOException {
    return null == format
      ? ConfigFile.load(source)
      : ConfigFile.load(source, format, parseOptions);
  }

  private ConfigFile loadCurrent() throws IOException {
    return null == this.format
      ? ConfigFile.load(this.source)
      : ConfigFile.load(this.source, this.format, this.parseOptions);
  }

  private long readModificationTime() {
    try {
      return Files.getLastModifiedTime(this.source).toMillis();
    } catch (final IOException ignored) {
      return 0L;
    }
  }

  private void tick() {
    if (this.closed.get()) {
      return;
    }
    try {
      final long current =
        Files.getLastModifiedTime(this.source).toMillis();
      final long previous = this.lastModified.get();
      if (current != previous
        && this.lastModified.compareAndSet(previous, current)) {
        this.onChange.accept(this.loadCurrent());
      }
    } catch (final IOException exception) {
      this.onError.accept(exception);
    } catch (final RuntimeException ignored) {
      // Never let a callback failure kill the scheduler.
    }
  }

  @Override
  public void close() {
    if (this.closed.compareAndSet(false, true)) {
      this.scheduler.shutdownNow();
    }
  }
}