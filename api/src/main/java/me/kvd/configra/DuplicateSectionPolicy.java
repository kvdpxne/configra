package me.kvd.configra;

/**
 * Behaviour when an INI file declares the same section header twice.
 * Ignored by {@link PropertiesFormat}, which has no sections.
 */
public enum DuplicateSectionPolicy {

  /**
   * Properties from later declarations are merged into the first. Default.
   */
  MERGE,

  /**
   * Duplicate section header raises
   * {@link me.kvdpxne.properties.exceptions.PropertyParseException}.
   */
  ABORT,

  /**
   * Properties of the duplicate section are dropped; the section is
   * declared again but its keys never reach the value map.
   */
  IGNORE
}