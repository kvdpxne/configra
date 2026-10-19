package me.kvd.properties.core;

import java.util.LinkedHashMap;
import java.util.Map;
import me.kvd.properties.exceptions.PropertyInterpolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigInterpolationTest {

  private static Map<String, String> map(final String... kv) {
    final Map<String, String> result = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      result.put(kv[i], kv[i + 1]);
    }
    return result;
  }

  @Test
  void noReferencesIdentity() {
    final Map<String, String> source = map("a", "hello", "b", "world");
    assertEquals(source, ConfigInterpolation.resolve(source));
  }

  @Test
  void simpleReference() {
    final Map<String, String> source = map(
      "base", "/opt/app",
      "data", "${base}/data");
    final Map<String, String> result = ConfigInterpolation.resolve(source);
    assertEquals("/opt/app/data", result.get("data"));
  }

  @Test
  void chainedReferences() {
    final Map<String, String> source = map(
      "a", "hello",
      "b", "${a} world",
      "c", "${b}!");
    final Map<String, String> result = ConfigInterpolation.resolve(source);
    assertEquals("hello world!", result.get("c"));
  }

  @Test
  void missingReferenceThrows() {
    final Map<String, String> source = map("a", "${missing}");
    assertThrows(PropertyInterpolationException.class,
      () -> ConfigInterpolation.resolve(source));
  }

  @Test
  void cycleThrows() {
    final Map<String, String> source = map(
      "a", "${b}",
      "b", "${a}");
    assertThrows(PropertyInterpolationException.class,
      () -> ConfigInterpolation.resolve(source));
  }

  @Test
  void selfCycleThrows() {
    final Map<String, String> source = map("a", "${a}");
    assertThrows(PropertyInterpolationException.class,
      () -> ConfigInterpolation.resolve(source));
  }
}