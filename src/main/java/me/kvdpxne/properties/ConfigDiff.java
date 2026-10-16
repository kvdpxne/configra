package me.kvdpxne.properties;

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
    previousValues = Collections.unmodifiableMap(
      new LinkedHashMap<>(previousValues));
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

  public enum ChangeKind {ADDED, REMOVED, CHANGED}

  public record Change(ChangeKind kind, String previous, String current) {
    public Change {
      Objects.requireNonNull(kind, "kind");
    }
  }
}