package me.kvd.properties;

/**
 * Behaviour when a {@code .properties} or INI file declares the same key
 * twice within the same effective scope.
 */
public enum DuplicatePolicy {

  /**
   * Later declaration wins; earlier values are dropped. Default.
   */
  LAST_WINS,

  /**
   * Earlier declaration wins; later duplicates are discarded.
   */
  FIRST_WINS,

  /**
   * Duplicate key raises
   * {@link me.kvdpxne.properties.exceptions.PropertyParseException} with
   * both line numbers.
   */
  ERROR
}