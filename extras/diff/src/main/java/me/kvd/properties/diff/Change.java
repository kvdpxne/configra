package me.kvd.properties.diff;

import java.util.Objects;

public record Change(ChangeKind kind, String previous, String current) {

  public Change {
    Objects.requireNonNull(kind, "kind");
  }
}