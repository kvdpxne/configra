package me.kvdpxne.properties;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared virtual-thread executor used by every asynchronous operation in
 * the library, plus helpers for thread-name identification.
 *
 * <h2>Lifecycle and races</h2>
 * <p>The executor is created lazily when this class is first loaded and
 * never restarted. {@link #close()} initiates shutdown; calls after the
 * first are no-ops. If a caller submits a task after {@link #close()},
 * {@code CompletableFuture} will report
 * {@link java.util.concurrent.RejectedExecutionException} — this is the
 * expected JDK behaviour and cannot be masked without wrapping the
 * executor (which would add a hot-path allocation). Callers in long-lived
 * containers should either never close the shared executor, or close it
 * only after all async work has drained. {@link #isClosed()} lets a caller
 * probe the state.</p>
 *
 * <p>All threads are daemons, so a JVM exit is never blocked even if
 * {@code close()} is never called.</p>
 */
public final class AsyncSupport {

  private static final String ASYNC_PREFIX = "properties-async-";
  private static final AtomicBoolean CLOSED = new AtomicBoolean(false);

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
   * by this library (async executor or watcher).
   */
  static boolean isInternalThreadName(final String threadName) {
    return null != threadName
      && (threadName.startsWith(ASYNC_PREFIX)
      || threadName.startsWith(PropertiesWatcher.THREAD_PREFIX));
  }

  /**
   * Initiates shutdown of the shared executor. Idempotent. After this
   * call, {@link #isClosed()} returns {@code true} and any subsequent
   * submission fails with {@code RejectedExecutionException}.
   */
  public static void close() {
    if (CLOSED.compareAndSet(false, true)) {
      VIRTUAL_EXECUTOR.shutdown();
    }
  }

  /**
   * Initiates shutdown and waits up to {@code timeoutMillis} for running
   * tasks to complete. Idempotent.
   */
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

  /**
   * Returns {@code true} once {@link #close()} has been called.
   */
  public static boolean isClosed() {
    return CLOSED.get();
  }
}