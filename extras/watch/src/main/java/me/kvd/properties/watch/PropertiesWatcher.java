package me.kvd.properties.watch;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import me.kvd.properties.core.ConfigFile;
import me.kvd.properties.diff.ConfigDiff;
import me.kvd.properties.options.ParseOptions;
import me.kvd.properties.spi.ConfigFormat;
import me.kvd.properties.util.AsyncSupport;

/**
 * Watches a config file and invokes a callback whenever its content changes.
 * Callbacks run on an internal daemon thread.
 */
public final class PropertiesWatcher implements AutoCloseable {

  static final String THREAD_PREFIX = "config-watcher-";

  static {
    AsyncSupport.registerInternalPrefix(THREAD_PREFIX);
  }

  private static final long POLL_INTERVAL_MS = 500L;
  private static final Consumer<IOException> SILENT_ERROR_HANDLER =
    ignored -> {
      // Silent by default.
    };

  public enum WatchMode {
    POLLING,
    EVENT_DRIVEN
  }

  private final Path source;
  private final ConfigFormat format;
  private final ParseOptions parseOptions;
  private final WatchMode mode;
  private final long pollIntervalMs;
  private final Consumer<ConfigFile> onChange;
  private final Consumer<IOException> onError;

  private final ScheduledExecutorService scheduler;
  private final AtomicLong lastModified = new AtomicLong();

  private final WatchService watchService;
  private final Thread eventThread;

  private final AtomicBoolean closed = new AtomicBoolean(false);

  private PropertiesWatcher(final Path source,
                            final ConfigFormat format,
                            final ParseOptions parseOptions,
                            final WatchMode mode,
                            final long pollIntervalMs,
                            final Consumer<ConfigFile> onChange,
                            final Consumer<IOException> onError)
    throws IOException {
    this.source = source;
    this.format = format;
    this.parseOptions = parseOptions;
    this.mode = mode;
    this.pollIntervalMs = pollIntervalMs;
    this.onChange = onChange;
    this.onError = onError;

    if (WatchMode.EVENT_DRIVEN == mode) {
      final WatchService service = FileSystems.getDefault().newWatchService();
      boolean registered = false;
      try {
        final Path dir = source.toAbsolutePath().getParent();
        if (null == dir) {
          throw new IOException(
            "Cannot resolve parent directory for " + source);
        }
        dir.register(service,
          StandardWatchEventKinds.ENTRY_MODIFY,
          StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_DELETE);
        registered = true;
      } finally {
        if (!registered) {
          try {
            service.close();
          } catch (final IOException ignored) {
            // Best-effort cleanup.
          }
        }
      }
      this.watchService = service;
      this.scheduler = null;
      this.eventThread = new Thread(this::eventLoop,
        THREAD_PREFIX + source.getFileName());
      this.eventThread.setDaemon(true);
    } else {
      this.watchService = null;
      this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable,
          THREAD_PREFIX + source.getFileName());
        thread.setDaemon(true);
        return thread;
      });
      this.eventThread = null;
      this.lastModified.set(this.readModificationTime());
    }
  }

  // ----- Factories: default (POLLING) -----

  public static PropertiesWatcher watch(final Path source,
                                        final Consumer<ConfigFile> onChange)
    throws IOException {
    return watch(source, WatchMode.POLLING, null, null, onChange,
      SILENT_ERROR_HANDLER);
  }

  public static PropertiesWatcher watch(final Path source,
                                        final Consumer<ConfigFile> onChange,
                                        final Consumer<IOException> onError)
    throws IOException {
    return watch(source, WatchMode.POLLING, null, null, onChange, onError);
  }

  public static PropertiesWatcher watch(final Path source,
                                        final WatchMode mode,
                                        final Consumer<ConfigFile> onChange)
    throws IOException {
    return watch(source, mode, null, null, onChange, SILENT_ERROR_HANDLER);
  }

  public static PropertiesWatcher watch(final Path source,
                                        final WatchMode mode,
                                        final Consumer<ConfigFile> onChange,
                                        final Consumer<IOException> onError)
    throws IOException {
    return watch(source, mode, null, null, onChange, onError);
  }

  public static PropertiesWatcher watch(final Path source,
                                        final ParseOptions parseOptions,
                                        final Consumer<ConfigFile> onChange)
    throws IOException {
    return watch(source, WatchMode.POLLING, null, parseOptions, onChange,
      SILENT_ERROR_HANDLER);
  }

  public static PropertiesWatcher watch(final Path source,
                                        final WatchMode mode,
                                        final ConfigFormat format,
                                        final ParseOptions parseOptions,
                                        final Consumer<ConfigFile> onChange,
                                        final Consumer<IOException> onError)
    throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(onChange, "onChange");
    Objects.requireNonNull(onError, "onError");
    if (!Files.isRegularFile(source)) {
      throw new IOException("Not a regular file: " + source);
    }
    final PropertiesWatcher watcher = new PropertiesWatcher(
      source, format, parseOptions, mode,
      POLL_INTERVAL_MS, onChange, onError);
    watcher.start();
    return watcher;
  }

  // ----- Diff-based factories -----

  public static PropertiesWatcher watchDiff(final Path source,
                                            final Consumer<ConfigDiff> onChange)
    throws IOException {
    return watchDiff(source, WatchMode.POLLING, null, null,
      onChange, SILENT_ERROR_HANDLER);
  }

  public static PropertiesWatcher watchDiff(final Path source,
                                            final WatchMode mode,
                                            final Consumer<ConfigDiff> onChange)
    throws IOException {
    return watchDiff(source, mode, null, null,
      onChange, SILENT_ERROR_HANDLER);
  }

  public static PropertiesWatcher watchDiff(final Path source,
                                            final Consumer<ConfigDiff> onChange,
                                            final Consumer<IOException> onError)
    throws IOException {
    return watchDiff(source, WatchMode.POLLING, null, null, onChange, onError);
  }

  public static PropertiesWatcher watchDiff(final Path source,
                                            final WatchMode mode,
                                            final Consumer<ConfigDiff> onChange,
                                            final Consumer<IOException> onError)
    throws IOException {
    return watchDiff(source, mode, null, null, onChange, onError);
  }

  public static PropertiesWatcher watchDiff(final Path source,
                                            final WatchMode mode,
                                            final ConfigFormat format,
                                            final ParseOptions parseOptions,
                                            final Consumer<ConfigDiff> onChange,
                                            final Consumer<IOException> onError)
    throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(onChange, "onChange");
    Objects.requireNonNull(onError, "onError");
    final AtomicReference<ConfigFile> previous = new AtomicReference<>(
      loadInitial(source, format, parseOptions));
    return watch(source, mode, format, parseOptions, updated -> {
      final ConfigFile old = previous.getAndSet(updated);
      if (null == old) {
        return;
      }
      final ConfigDiff diff = old.diff(updated);
      if (!diff.isEmpty()) {
        onChange.accept(diff);
      }
    }, onError);
  }

  // ----- Internal -----

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

  private void start() {
    if (WatchMode.EVENT_DRIVEN == this.mode) {
      this.eventThread.start();
    } else {
      this.scheduler.scheduleWithFixedDelay(
        this::pollTick,
        this.pollIntervalMs,
        this.pollIntervalMs,
        TimeUnit.MILLISECONDS);
    }
  }

  private void pollTick() {
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

  private void eventLoop() {
    final Path fileName = this.source.getFileName();
    while (!this.closed.get()) {
      final WatchKey key;
      try {
        key = this.watchService.take();
      } catch (final InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      } catch (final ClosedWatchServiceException exception) {
        return;
      }
      if (null == key) {
        continue;
      }
      boolean relevant = false;
      try {
        for (final WatchEvent<?> event : key.pollEvents()) {
          if (StandardWatchEventKinds.OVERFLOW == event.kind()) {
            relevant = true;
            continue;
          }
          final Object context = event.context();
          if (context instanceof Path changed
            && changed.getFileName().equals(fileName)) {
            relevant = true;
          }
        }
      } finally {
        final boolean valid = key.reset();
        if (!valid) {
          return;
        }
      }
      if (relevant && !this.closed.get()) {
        try {
          this.onChange.accept(this.loadCurrent());
        } catch (final IOException exception) {
          this.onError.accept(exception);
        } catch (final RuntimeException ignored) {
          // Never let a callback failure kill the watcher thread.
        }
      }
    }
  }

  @Override
  public void close() {
    if (!this.closed.compareAndSet(false, true)) {
      return;
    }
    if (null != this.scheduler) {
      this.scheduler.shutdownNow();
    }
    if (null != this.watchService) {
      try {
        this.watchService.close();
      } catch (final IOException ignored) {
        // Best-effort cleanup.
      }
    }
    if (null != this.eventThread) {
      this.eventThread.interrupt();
    }
  }
}