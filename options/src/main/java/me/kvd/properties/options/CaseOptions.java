package me.kvd.properties.options;

/**
 * Case sensitivity for keys and section names.
 */
public record CaseOptions(boolean insensitiveKeys, boolean insensitiveSections) {

  /**
   * Unix-style: case-sensitive everywhere.
   */
  public static final CaseOptions DEFAULT = new CaseOptions(false, false);

  /**
   * Windows-style: case-insensitive everywhere.
   */
  public static final CaseOptions WINDOWS = new CaseOptions(true, true);
}