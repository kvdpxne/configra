package me.kvdpxne.properties;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Shared virtual-thread executor used by every asynchronous operation in
 * the library, plus helpers for thread-name identification.
 *
 * <p>Virtual threads are cheap; callers may submit tens of thousands of
 * concurrent operations without concern. The executor is not shut down
 * automatically — closing it via {@link #close()} releases the backing
 * resources, but the JVM will exit cleanly even without it because all
 * threads are daemons.</p>
 */
public final class AsyncSupport {

  private static final String ASYNC_PREFIX = "properties-async-";

  static final ExecutorService VIRTUAL_EXECUTOR =
    Executors.newThreadPerTaskExecutor(
      Thread.ofVirtual()
        .name(ASYNC_PREFIX, 0)
        .factory());

  private AsyncSupport() {
    throw new AssertionError("No instances.");
  }

  /**
   * Returns the shared virtual-thread executor.
   */
  public static ExecutorService executor() {
    return VIRTUAL_EXECUTOR;
  }

  /**
   * Returns {@code true} if {@code threadName} belongs to a thread owned
   * by this library (async executor or watcher). Used by the strict-thread
   * guard in {@link ConfigFile#save}.
   */
  static boolean isInternalThreadName(final String threadName) {
    return null != threadName
      && (threadName.startsWith(ASYNC_PREFIX)
      || threadName.startsWith(PropertiesWatcher.THREAD_PREFIX));
  }

  /**
   * Shuts down the shared executor. Safe to call multiple times. After
   * this call, {@link ConfigFile#loadAsync} and friends will still submit
   * to the (now shutdown) executor and complete exceptionally.
   */
  public static void close() {
    VIRTUAL_EXECUTOR.shutdown();
  }

  /**
   * Shuts down the shared executor and waits up to {@code timeout}
   * milliseconds for tasks to finish.
   */
  public static void close(final long timeoutMillis) {
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
}