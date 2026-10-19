package me.kvd.properties;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyKeyTest {

  @Test
  void factoriesWithDefaults() {
    assertEquals("hello", PropertyKey.string("k", "hello").defaultValue());
    assertEquals(42, PropertyKey.integer("k", 42).defaultValue());
    assertTrue(PropertyKey.bool("k", true).defaultValue());
    assertEquals(1.5f, PropertyKey.floatKey("k", 1.5f).defaultValue());
    assertEquals(List.of("a"), PropertyKey.stringList("k", List.of("a")).defaultValue());
  }

  @Test
  void factoriesWithoutDefaults() {
    assertFalse(PropertyKey.string("k").hasDefault());
    assertFalse(PropertyKey.integer("k").hasDefault());
    assertTrue(PropertyKey.stringList("k").hasDefault());
  }

  @Test
  void blankNameThrows() {
    assertThrows(IllegalArgumentException.class, () -> PropertyKey.string(""));
    assertThrows(IllegalArgumentException.class, () -> PropertyKey.string("   "));
  }

  @Test
  void whitespaceAroundNameThrows() {
    assertThrows(IllegalArgumentException.class,
      () -> PropertyKey.string(" key"));
    assertThrows(IllegalArgumentException.class,
      () -> PropertyKey.string("key "));
  }

  @Test
  void controlCharactersInNameThrow() {
    assertThrows(IllegalArgumentException.class,
      () -> PropertyKey.string("key\n"));
    assertThrows(IllegalArgumentException.class,
      () -> PropertyKey.string("key\twith\ttabs"));
  }

  @Test
  void withDefaultReturnsCopy() {
    final PropertyKey<String> original = PropertyKey.string("k");
    final PropertyKey<String> copy = original.withDefault("x");
    assertNotSame(original, copy);
    assertNull(original.defaultValue());
    assertEquals("x", copy.defaultValue());
  }

  @Test
  void equalityIsStructural() {
    final PropertyKey<String> a = PropertyKey.string("k", "x");
    final PropertyKey<String> b = PropertyKey.string("k", "x");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void durationFactory() {
    final PropertyKey<Duration> key =
      PropertyKey.duration("timeout", Duration.ofSeconds(30));
    assertEquals(Duration.ofSeconds(30), key.defaultValue());
  }
}