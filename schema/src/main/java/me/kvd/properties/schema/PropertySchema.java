package me.kvd.properties.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.kvd.properties.PropertyKey;
import me.kvd.properties.core.ConfigFile;
import me.kvd.properties.exceptions.PropertyTypeException;
import me.kvd.properties.exceptions.PropertyValidationException;

public final class PropertySchema {

  private final List<PropertyKey<?>> requiredKeys;
  private final List<PropertyKey<?>> optionalKeys;

  private PropertySchema(final List<PropertyKey<?>> required,
                         final List<PropertyKey<?>> optional) {
    this.requiredKeys = List.copyOf(required);
    this.optionalKeys = List.copyOf(optional);
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<PropertyKey<?>> requiredKeys() {
    return this.requiredKeys;
  }

  public List<PropertyKey<?>> optionalKeys() {
    return this.optionalKeys;
  }

  public List<String> missing(final ConfigFile file) {
    Objects.requireNonNull(file, "file");
    final List<String> missing = new ArrayList<>();
    for (final PropertyKey<?> key : this.requiredKeys) {
      if (!key.hasDefault() && !file.contains(key)) {
        missing.add(key.name());
      }
    }
    return List.copyOf(missing);
  }

  /**
   * @throws PropertyValidationException if any required key is absent
   */
  public void validate(final ConfigFile file) {
    final List<String> missing = this.missing(file);
    if (!missing.isEmpty()) {
      throw new PropertyValidationException(missing);
    }
  }

  /**
   * Attempts to decode every required or optional key present in
   * {@code file}. Reports the first failure as a
   * {@link PropertyTypeException} carrying the key and offending raw
   * value.
   *
   * <p>Call after {@link #validate} to catch typos in values before the
   * plugin starts depending on them.</p>
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void validateTypes(final ConfigFile file) {
    Objects.requireNonNull(file, "file");
    for (final PropertyKey<?> key : this.requiredKeys) {
      this.validateOne(file, key);
    }
    for (final PropertyKey<?> key : this.optionalKeys) {
      this.validateOne(file, key);
    }
  }

  private void validateOne(final ConfigFile file, final PropertyKey<?> key) {
    if (!file.contains(key)) {
      return;
    }
    try {
      file.require(key);
    } catch (final PropertyTypeException typed) {
      throw typed;
    } catch (final RuntimeException failure) {
      throw new PropertyTypeException(
        key.name(),
        file.find(key.name()).orElse(null),
        "Failed to decode '" + key.name() + '\'',
        failure);
    }
  }

  public static final class Builder {

    private final List<PropertyKey<?>> required = new ArrayList<>();
    private final List<PropertyKey<?>> optional = new ArrayList<>();

    public Builder require(final PropertyKey<?> key) {
      Objects.requireNonNull(key, "key");
      this.required.add(key);
      return this;
    }

    public Builder optional(final PropertyKey<?> key) {
      Objects.requireNonNull(key, "key");
      this.optional.add(key);
      return this;
    }

    public PropertySchema build() {
      return new PropertySchema(this.required, this.optional);
    }
  }
}