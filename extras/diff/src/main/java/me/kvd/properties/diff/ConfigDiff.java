package me.kvd.properties.diff;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Difference between two {@link ConfigFile} states. Keys are fully qualified
 * (see {@link PropertyNode#qualifiedKey()}).
 *
 * <p>Iteration order of every map mirrors the order in which keys were
 * encountered when the diff was computed. Unlike {@link Map#copyOf}, which
 * does not preserve insertion order, this class uses unmodifiable
 * {@link LinkedHashMap} views to keep diffs deterministic — critical when
 * the diff is logged or shown to a user.</p>
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

    added = Collections.unmodifiableMap(new LinkedHashMap<>(added));
    removed = Collections.unmodifiableMap(new LinkedHashMap<>(removed));
    changed = Collections.unmodifiableMap(new LinkedHashMap<>(changed));
    previousValues = Collections.unmodifiableMap(new LinkedHashMap<>(previousValues));
  }

  /**
   * Computes the difference between two value maps. Keys absent from
   * {@code previous} become {@code added}; keys absent from {@code current}
   * become {@code removed}; keys whose values differ become {@code changed}.
   * Iteration order is preserved for all four maps.
   */
  public static ConfigDiff of(final Map<String, String> previous,
                              final Map<String, String> current) {
    Objects.requireNonNull(previous, "previous");
    Objects.requireNonNull(current, "current");

    final Map<String, String> added = new LinkedHashMap<>();
    final Map<String, String> removed = new LinkedHashMap<>();
    final Map<String, String> changed = new LinkedHashMap<>();
    final Map<String, String> previousValues = new LinkedHashMap<>();

    for (final Map.Entry<String, String> entry : current.entrySet()) {
      final String key = entry.getKey(),
        newValue = entry.getValue(),
        oldValue = previous.get(key);

      if (null == oldValue) {
        added.put(key, newValue);
        continue;
      }

      if (!oldValue.equals(newValue)) {
        changed.put(key, newValue);
        previousValues.put(key, oldValue);
      }
    }
    for (final Map.Entry<String, String> entry : previous.entrySet()) {
      final String key = entry.getKey();
      if (!current.containsKey(key)) {
        removed.put(key, entry.getValue());
      }
    }
    return new ConfigDiff(added, removed, changed, previousValues);
  }

  public boolean isEmpty() {
    return this.added.isEmpty()
      && this.removed.isEmpty()
      && this.changed.isEmpty();
  }

  public int size() {
    return this.added.size() + this.removed.size() + this.changed.size();
  }

  /**
   * Convenience accessor for the previous value of a changed key.
   */
  public String previous(final String key) {
    Objects.requireNonNull(key, "key");
    return this.previousValues.get(key);
  }

  /**
   * Returns every key touched by this diff in a single ordered map.
   * Order: added first, then changed, then removed — matching the natural
   * presentation order when reporting changes to a user.
   */
  public Map<String, Change> asMap() {
    final Map<String, Change> result = new LinkedHashMap<>(
      this.added.size() + this.changed.size() + this.removed.size());
    this.added.forEach((k, v) -> result.put(k,
      new Change(ChangeKind.ADDED, null, v)));
    this.changed.forEach((k, v) -> result.put(k,
      new Change(ChangeKind.CHANGED, this.previousValues.get(k), v)));
    this.removed.forEach((k, v) -> result.put(k,
      new Change(ChangeKind.REMOVED, v, null)));
    return Collections.unmodifiableMap(result);
  }
}