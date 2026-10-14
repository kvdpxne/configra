package me.kvdpxne.properties;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thread-safe lazy holder. The supplier runs at most once; subsequent reads
 * return the cached value without locking.
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

  public static <T> Lazy<T> of(final Supplier<T> supplier) {
    return new Lazy<>(supplier, false);
  }

  /**
   * Variant that allows the supplier to return {@code null}. In that case
   * the sentinel is cached and subsequent {@link #get} calls return
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