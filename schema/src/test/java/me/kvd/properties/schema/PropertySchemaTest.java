package me.kvd.properties.schema;

import java.util.List;
import me.kvd.properties.PropertyKey;
import me.kvd.properties.core.ConfigFile;
import me.kvd.properties.exceptions.PropertyValidationException;
import me.kvd.properties.format.properties.PropertiesFormat;
import me.kvd.properties.spi.ConfigFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertySchemaTest {

  @Test
  void requiredKeysTracked() {
    final PropertySchema schema = PropertySchema.builder()
      .require(PropertyKey.string("a"))
      .require(PropertyKey.string("b"))
      .build();
    assertEquals(2, schema.requiredKeys().size());
  }

  @Test
  void missingDetectsAbsent() {
    final PropertySchema schema = PropertySchema.builder()
      .require(PropertyKey.string("a"))
      .require(PropertyKey.string("b"))
      .build();
    final ConfigFile config = ConfigFile.empty(PropertiesFormat.INSTANCE);
    assertEquals(List.of("a", "b"), schema.missing(config));
  }

  @Test
  void defaultsSatisfy() {
    final PropertySchema schema = PropertySchema.builder()
      .require(PropertyKey.string("a", "default"))
      .build();
    final ConfigFile config = ConfigFile.empty(PropertiesFormat.INSTANCE);
    assertTrue(schema.missing(config).isEmpty());
  }

  @Test
  void validateThrowsOnMissing() {
    final PropertySchema schema = PropertySchema.builder()
      .require(PropertyKey.string("required"))
      .build();
    final ConfigFile config = ConfigFile.empty(PropertiesFormat.INSTANCE);
    assertThrows(PropertyValidationException.class,
      () -> schema.validate(config));
  }
}