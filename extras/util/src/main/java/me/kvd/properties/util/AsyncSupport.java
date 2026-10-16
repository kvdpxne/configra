package me.kvd.properties.util;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AsyncSupport {

  private static final String ASYNC_PREFIX = "properties-async-";
  private static final AtomicBoolean CLOSED = new AtomicBoolean(false);
  private static final CopyOnWriteArraySet<String> EXTERNAL_PREFIXES =
    new CopyOnWriteArraySet<>();

  public static final ExecutorService VIRTUAL_EXECUTOR =
    Executors.newThreadPerTaskExecutor(
      Thread.ofVirtual().name(ASYNC_PREFIX, 0).factory());

  private AsyncSupport() {
    throw new AssertionError("No instances.");
  }

  public static ExecutorService executor() {
    return VIRTUAL_EXECUTOR;
  }

  /**
   * Registers an additional thread-name prefix as "internal". Modules
   * outside this one (e.g. watcher) call this once at class load so
   * {@link #isInternalThreadName(String)} recognises their threads.
   */
  public static void registerInternalPrefix(final String prefix) {
    if (null != prefix && !prefix.isEmpty()) {
      EXTERNAL_PREFIXES.add(prefix);
    }
  }

  public static boolean isInternalThreadName(final String threadName) {
    if (null == threadName) {
      return false;
    }
    if (threadName.startsWith(ASYNC_PREFIX)) {
      return true;
    }
    for (final String prefix : EXTERNAL_PREFIXES) {
      if (threadName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  public static void close() {
    if (CLOSED.compareAndSet(false, true)) {
      VIRTUAL_EXECUTOR.shutdown();
    }
  }

  public static void close(final long timeoutMillis) {
    if (!CLOSED.compareAndSet(false, true)) {
      return;
    }
    VIRTUAL_EXECUTOR.shutdown();
    try {
      if (!VIRTUAL_EXECUTOR.awaitTermination(
        timeoutMillis, TimeUnit.MILLISECONDS)) {
        VIRTUAL_EXECUTOR.shutdownNow();
      }
    } catch (final InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      VIRTUAL_EXECUTOR.shutdownNow();
    }
  }

  public static boolean isClosed() {
    return CLOSED.get();
  }
}