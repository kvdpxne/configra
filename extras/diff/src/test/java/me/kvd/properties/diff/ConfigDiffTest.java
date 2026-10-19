package me.kvd.properties.diff;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDiffTest {

  private static Map<String, String> map(final String... kv) {
    final Map<String, String> result = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      result.put(kv[i], kv[i + 1]);
    }
    return result;
  }

  @Test
  void detectsAdded() {
    final ConfigDiff diff = ConfigDiff.of(
      map("a", "1"),
      map("a", "1", "b", "2"));
    assertEquals(Set.of("b"), diff.added().keySet());
    assertTrue(diff.changed().isEmpty());
    assertTrue(diff.removed().isEmpty());
  }

  @Test
  void detectsRemoved() {
    final ConfigDiff diff = ConfigDiff.of(
      map("a", "1", "b", "2"),
      map("a", "1"));
    assertEquals(Set.of("b"), diff.removed().keySet());
  }

  @Test
  void detectsChanged() {
    final ConfigDiff diff = ConfigDiff.of(
      map("a", "1"),
      map("a", "9"));
    assertEquals(Set.of("a"), diff.changed().keySet());
    assertEquals("1", diff.previous("a"));
  }

  @Test
  void emptyWhenIdentical() {
    final ConfigDiff diff = ConfigDiff.of(
      map("a", "1"),
      map("a", "1"));
    assertTrue(diff.isEmpty());
    assertEquals(0, diff.size());
  }

  @Test
  void size() {
    final ConfigDiff diff = ConfigDiff.of(
      map("a", "1", "b", "2", "c", "3"),
      map("a", "1", "b", "9", "d", "4"));
    assertEquals(3, diff.size());
  }

  @Test
  void preservesIterationOrder() {
    final ConfigDiff diff = ConfigDiff.of(
      map("z", "1", "y", "2", "x", "3"),
      map("a", "9", "b", "9"));
    // removed preserves the source order: z, y, x
    assertEquals(java.util.List.of("z", "y", "x"),
      new java.util.ArrayList<>(diff.removed().keySet()));
  }

  @Test
  void disabledTracking() {
    final ConfigDiff diff = ConfigDiff.of(
      map("a", "1"),
      map("a", "1"),
      Set.of(),
      Set.of("a"));
    assertEquals(Set.of("a"), diff.disabled().keySet());
    assertEquals(ChangeKind.DISABLED,
      diff.disabled().get("a").kind());
  }

  @Test
  void enabledTracking() {
    final ConfigDiff diff = ConfigDiff.of(
      map("a", "1"),
      map("a", "1"),
      Set.of("a"),
      Set.of());
    assertEquals(Set.of("a"), diff.disabled().keySet());
    assertEquals(ChangeKind.ENABLED,
      diff.disabled().get("a").kind());
  }
}