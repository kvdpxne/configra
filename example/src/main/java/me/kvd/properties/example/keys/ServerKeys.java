package me.kvd.properties.example.keys;

import java.time.Duration;
import java.util.List;
import me.kvd.properties.ConfigKeyDeclaration;
import me.kvd.properties.PropertyKey;

/**
 * Enum-declared keys for Demo 02. Names are fully qualified
 * ({@code section.key}); {@code EnumIniBuilder} splits them into INI
 * sections.
 */
public enum ServerKeys implements ConfigKeyDeclaration {

  MODE(PropertyKey.string("general.mode", "MINECRAFT"),
    "Available modes: MINECRAFT, CUSTOM, DISABLED."),

  DEBUG(PropertyKey.bool("general.debug", false),
    "Verbose logging. Enable only for diagnostics."),

  HOST(PropertyKey.string("network.host", "0.0.0.0"),
    "Interface to bind."),

  PORT(PropertyKey.integer("network.port", 25565),
    "TCP port to listen on."),

  SPOOF_ENABLED(PropertyKey.bool("spoof.enabled", true),
    "Serve a fake plugin list instead of an error."),

  SPOOF_PLUGINS(PropertyKey.stringList("spoof.plugins",
    List.of("Alpha", "Beta", "Gamma")),
    "Fake names, comma-separated.",
    "Escape a literal comma as \\,."),

  NOTIFICATION_WINDOW(PropertyKey.duration("notifications.window",
    Duration.ofSeconds(60)),
    "Rolling window. Accepts 30s, 5m, 1h30m, or ISO-8601 (PT30S).");

  private final PropertyKey<?> key;
  private final String[] comments;

  ServerKeys(final PropertyKey<?> key, final String... comments) {
    this.key = key;
    this.comments = comments;
  }

  @Override
  public PropertyKey<?> key() {
    return this.key;
  }

  @Override
  public String comment() {
    return String.join("\n", this.comments);
  }
}