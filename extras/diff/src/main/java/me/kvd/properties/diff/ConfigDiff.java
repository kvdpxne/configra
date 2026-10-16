package me.kvd.properties.diff;

import com.sun.security.auth.login.ConfigFile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Difference between two {@link ConfigFile} states. All maps preserve
 * iteration order.
 *
 * <p>Disabled-node tracking: {@link #disabled()} lists keys that became
 * disabled or re-enabled, along with the direction. Populated only by
 * {@link #between(ConfigFile, ConfigFile)}; {@link #of(Map, Map)} leaves it
 * empty since it operates on value maps only.</p>
 */
public record ConfigDiff(
  Map<String, String> added,
  Map<String, String> removed,
  Map<String, String> changed,
  Map<String, String> previousValues,
  Map<String, Change> disabled
) {

  public ConfigDiff {
    Objects.requireNonNull(added, "added");
    Objects.requireNonNull(removed, "removed");
    Objects.requireNonNull(changed, "changed");
    Objects.requireNonNull(previousValues, "previousValues");
    Objects.requireNonNull(disabled, "disabled");
    added = Collections.unmodifiableMap(new LinkedHashMap<>(added));
    removed = Collections.unmodifiableMap(new LinkedHashMap<>(removed));
    changed = Collections.unmodifiableMap(new LinkedHashMap<>(changed));
    previousValues = Collections.unmodifiableMap(
      new LinkedHashMap<>(previousValues));
    disabled = Collections.unmodifiableMap(new LinkedHashMap<>(disabled));
  }

  /** Backward-compatible factory without disabled information. */
  public static ConfigDiff of(final Map<String, String> previous,
                              final Map<String, String> current) {
    return of(previous, current, Set.of(), Set.of());
  }

  /** Factory with explicit disabled sets. */
  public static ConfigDiff of(final Map<String, String> previous,
                              final Map<String, String> current,
                              final Set<String> previousDisabled,
                              final Set<String> currentDisabled) {
    Objects.requireNonNull(previous, "previous");
    Objects.requireNonNull(current, "current");
    Objects.requireNonNull(previousDisabled, "previousDisabled");
    Objects.requireNonNull(currentDisabled, "currentDisabled");

    final Map<String, String> added = new LinkedHashMap<>();
    final Map<String, String> removed = new LinkedHashMap<>();
    final Map<String, String> changed = new LinkedHashMap<>();
    final Map<String, String> previousValues = new LinkedHashMap<>();

    for (final Map.Entry<String, String> entry : current.entrySet()) {
      final String key = entry.getKey();
      final String newValue = entry.getValue();
      final String oldValue = previous.get(key);
      if (null == oldValue) {
        added.put(key, newValue);
      } else if (!oldValue.equals(newValue)) {
        changed.put(key, newValue);
        previousValues.put(key, oldValue);
      }
    }
    for (final Map.Entry<String, String> entry : previous.entrySet()) {
      if (!current.containsKey(entry.getKey())) {
        removed.put(entry.getKey(), entry.getValue());
      }
    }

    final Map<String, Change> disabled = new LinkedHashMap<>();
    for (final String key : currentDisabled) {
      if (!previousDisabled.contains(key)) {
        disabled.put(key, new Change(
          ChangeKind.DISABLED,
          previous.get(key),
          current.get(key)));
      }
    }
    for (final String key : previousDisabled) {
      if (!currentDisabled.contains(key)) {
        disabled.put(key, new Change(
          ChangeKind.ENABLED,
          previous.get(key),
          current.get(key)));
      }
    }
    return new ConfigDiff(added, removed, changed, previousValues, disabled);
  }

  public boolean isEmpty() {
    return this.added.isEmpty()
      && this.removed.isEmpty()
      && this.changed.isEmpty()
      && this.disabled.isEmpty();
  }

  public int size() {
    return this.added.size() + this.removed.size()
      + this.changed.size() + this.disabled.size();
  }

  public String previous(final String key) {
    Objects.requireNonNull(key, "key");
    return this.previousValues.get(key);
  }

  public Map<String, Change> asMap() {
    final Map<String, Change> result = new LinkedHashMap<>(
      this.added.size() + this.changed.size()
        + this.removed.size() + this.disabled.size());
    this.added.forEach((k, v) -> result.put(k,
      new Change(ChangeKind.ADDED, null, v)));
    this.changed.forEach((k, v) -> result.put(k,
      new Change(ChangeKind.CHANGED, this.previousValues.get(k), v)));
    this.removed.forEach((k, v) -> result.put(k,
      new Change(ChangeKind.REMOVED, v, null)));
    this.disabled.forEach(result::put);
    return Collections.unmodifiableMap(result);
  }
}