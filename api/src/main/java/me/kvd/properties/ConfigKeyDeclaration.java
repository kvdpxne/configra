package me.kvd.properties;

/**
 * Contract for enums that declare the schema of a config file.
 *
 * <p>Prefer passing the enum constant directly to
 * {@link ConfigFile#require(ConfigKeyDeclaration)} et al., which infer the
 * value type from the assignment target. Use {@link #typedKey()} only when
 * a raw {@link PropertyKey} reference is needed (e.g. building a
 * {@link PropertySchema}).</p>
 */
public interface ConfigKeyDeclaration {

  PropertyKey<?> key();

  default String comment() {
    return null;
  }

  /**
   * Returns {@link #key()} cast to the caller-declared value type. The
   * cast is erased by the compiler: no runtime check, no allocation.
   * A mismatch surfaces as {@link ClassCastException} the moment the
   * value is actually used, never in this method.
   */
  @SuppressWarnings("unchecked")
  default <T> PropertyKey<T> typedKey() {
    return (PropertyKey<T>) this.key();
  }
}