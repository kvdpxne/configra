package me.kvdpxne.properties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Difference between two {@link ConfigFile} states. Keys are fully qualified
 * (see {@link PropertyNode#qualifiedKey()}).
 */
public record ConfigDiff(
  Map<String, String> added,
  Map<String, String> removed,
  Map<String, String> changed,
  Map<String, String> previousValues
) {

  public ConfigDiff {
    Objects.requireNonNull(added, "added");
    Objects.requireNonNull(removed, "removed");
    Objects.requireNonNull(changed, "changed");
    Objects.requireNonNull(previousValues, "previousValues");
    added = Map.copyOf(added);
    removed = Map.copyOf(removed);
    changed = Map.copyOf(changed);
    previousValues = Map.copyOf(previousValues);
  }

  public boolean isEmpty() {
    return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
  }

  public int size() {
    return added.size() + removed.size() + changed.size();
  }

  /**
   * Convenience accessor for the previous value of a changed key.
   */
  public String previous(final String key) {
    Objects.requireNonNull(key, "key");
    return previousValues.get(key);
  }

  /**
   * Returns all keys touched by this diff in insertion order.
   */
  public Map<String, Change> asMap() {
    final Map<String, Change> result = new LinkedHashMap<>();
    added.forEach((k, v) -> result.put(k, new Change(ChangeKind.ADDED, null, v)));
    changed.forEach((k, v) -> result.put(k,
      new Change(ChangeKind.CHANGED, previousValues.get(k), v)));
    removed.forEach((k, v) -> result.put(k, new Change(ChangeKind.REMOVED, v, null)));
    return Map.copyOf(result);
  }

  public enum ChangeKind {ADDED, REMOVED, CHANGED}

  public record Change(ChangeKind kind, String previous, String current) {
    public Change {
      Objects.requireNonNull(kind, "kind");
    }
  }
}