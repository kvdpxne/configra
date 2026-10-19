package me.kvd.properties.schema;

import me.kvd.properties.ConfigKeyDeclaration;
import me.kvd.properties.PropertyKey;
import me.kvd.properties.core.ConfigFile;
import me.kvd.properties.exceptions.PropertyValidationException;
import me.kvd.properties.format.properties.PropertiesFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumSchemaTest {

  enum Keys implements ConfigKeyDeclaration {
    NAME(PropertyKey.string("name", "default"), "The name."),
    PORT(PropertyKey.integer("port", 8080), "TCP port."),
    SECRET(PropertyKey.string("secret"), "Required, no default.");

    private final PropertyKey<?> key;
    private final String comment;

    Keys(final PropertyKey<?> key, final String comment) {
      this.key = key;
      this.comment = comment;
    }

    @Override
    public PropertyKey<?> key() {
      return this.key;
    }

    @Override
    public String comment() {
      return this.comment;
    }
  }

  @Test
  void constantsReflectEnum() {
    final EnumSchema<Keys> schema = EnumSchema.of(Keys.class);
    assertEquals(3, schema.constants().size());
  }

  @Test
  void defaultsContainsAllKeys() {
    final ConfigFile defaults = EnumSchema.of(Keys.class)
      .defaults()
      .build(PropertiesFormat.INSTANCE);
    assertTrue(defaults.contains("name"));
    assertTrue(defaults.contains("port"));
    assertFalse(defaults.contains("secret")); // no default
  }

  @Test
  void missingDetectsAbsentRequired() {
    final EnumSchema<Keys> schema = EnumSchema.of(Keys.class);
    final ConfigFile config = ConfigFile.empty(PropertiesFormat.INSTANCE);
    assertEquals(java.util.List.of("secret"), schema.missing(config));
  }

  @Test
  void validateThrowsOnMissing() {
    final EnumSchema<Keys> schema = EnumSchema.of(Keys.class);
    final ConfigFile config = ConfigFile.empty(PropertiesFormat.INSTANCE);
    assertThrows(PropertyValidationException.class,
      () -> schema.validate(config));
  }

  @Test
  void validatePassesWhenPresent() {
    final EnumSchema<Keys> schema = EnumSchema.of(Keys.class);
    final ConfigFile config = ConfigFile.empty(PropertiesFormat.INSTANCE)
      .with(PropertyKey.string("secret"), "yes");
    assertDoesNotThrow(() -> schema.validate(config));
  }
}