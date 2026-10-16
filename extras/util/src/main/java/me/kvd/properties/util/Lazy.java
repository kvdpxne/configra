package me.kvd.properties.util;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thread-safe lazy holder for consumer use.
 *
 * <p>The supplier runs at most once; subsequent reads return the cached
 * value without locking. Implementation uses double-checked locking on a
 * {@code volatile} field, so after the first successful {@link #get()} the
 * hot path is a plain volatile read.</p>
 *
 * <p><b>Scope.</b> This class is provided as a utility for callers who
 * need one-shot initialisation of expensive values in their own code
 * (for example, a plugin that lazily builds a plugin-name lookup table on
 * first use). The library itself does not use {@code Lazy} internally:
 * {@link me.kvdpxne.properties.ConfigFile} is fully eager after
 * {@code load}, and its decode cache is a {@link java.util.concurrent.ConcurrentHashMap}
 * keyed by {@link me.kvdpxne.properties.PropertyKey}.</p>
 *
 * @param <T> the value type
 */
public final class Lazy<T> {

  private static final Object NULL_SENTINEL = new Object();

  private volatile Supplier<T> supplier;
  private volatile T value;
  private final boolean allowNull;

  private Lazy(final Supplier<T> supplier, final boolean allowNull) {
    this.supplier = Objects.requireNonNull(supplier, "supplier");
    this.allowNull = allowNull;
  }

  /**
   * Creates a lazy holder whose supplier must not return {@code null}.
   */
  public static <T> Lazy<T> of(final Supplier<T> supplier) {
    return new Lazy<>(supplier, false);
  }

  /**
   * Creates a lazy holder whose supplier may return {@code null}. In that
   * case the sentinel is cached and subsequent {@link #get} calls return
   * {@code null} without re-invoking the supplier.
   */
  public static <T> Lazy<T> ofNullable(final Supplier<T> supplier) {
    return new Lazy<>(supplier, true);
  }

  @SuppressWarnings("unchecked")
  public T get() {
    final Object cached = this.value;
    if (null != cached) {
      return cached == NULL_SENTINEL ? null : (T) cached;
    }
    return this.initialise();
  }

  @SuppressWarnings("unchecked")
  private synchronized T initialise() {
    final Object cached = this.value;
    if (null != cached) {
      return cached == NULL_SENTINEL ? null : (T) cached;
    }
    final T computed = this.supplier.get();
    if (null == computed) {
      if (!this.allowNull) {
        throw new NullPointerException(
          "Lazy.of supplier returned null; use ofNullable");
      }
      this.value = (T) NULL_SENTINEL;
    } else {
      this.value = computed;
    }
    this.supplier = null;
    return computed;
  }

  public boolean isInitialised() {
    return null != this.value;
  }
}