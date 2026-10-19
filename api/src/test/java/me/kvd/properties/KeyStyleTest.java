package me.kvd.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyStyleTest {

  @Test
  void camelToKebab() {
    assertEquals("day-sync-mode",
      KeyStyle.KEBAB_CASE.fromCanonical("daySyncMode"));
    assertEquals("time-zone",
      KeyStyle.KEBAB_CASE.fromCanonical("timeZone"));
  }

  @Test
  void camelToSnake() {
    assertEquals("day_sync_mode",
      KeyStyle.SNAKE_CASE.fromCanonical("daySyncMode"));
  }

  @Test
  void camelToScreaming() {
    assertEquals("DAY_SYNC_MODE",
      KeyStyle.SCREAMING_SNAKE.fromCanonical("daySyncMode"));
  }

  @Test
  void camelToPascal() {
    assertEquals("DaySyncMode",
      KeyStyle.PASCAL_CASE.fromCanonical("daySyncMode"));
  }

  @Test
  void kebabToCamel() {
    assertEquals("daySyncMode",
      KeyStyle.KEBAB_CASE.toCanonical("day-sync-mode"));
  }

  @Test
  void snakeToCamel() {
    assertEquals("daySyncMode",
      KeyStyle.SNAKE_CASE.toCanonical("day_sync_mode"));
  }

  @Test
  void screamingToCamel() {
    assertEquals("daySyncMode",
      KeyStyle.SCREAMING_SNAKE.toCanonical("DAY_SYNC_MODE"));
  }

  @Test
  void acronyms() {
    // Acronym as input: split preserves the boundary; fromCanonical
    // produces kebab with the acronym lowered (no capitals survive).
    assertEquals("html-parser",
      KeyStyle.KEBAB_CASE.fromCanonical("HTMLParser"));

    // Snake input has no acronym info; result is lowercased per word.
    assertEquals("htmlParser",
      KeyStyle.SNAKE_CASE.toCanonical("html_parser"));

    // Pascal from camel: only the first letter is capitalised.
    assertEquals("HtmlParser",
      KeyStyle.PASCAL_CASE.fromCanonical("htmlParser"));
  }

  @Test
  void singleLowercaseWordUnchanged() {
    assertEquals("debug", KeyStyle.KEBAB_CASE.fromCanonical("debug"));
    assertEquals("debug", KeyStyle.SNAKE_CASE.fromCanonical("debug"));
    assertEquals("debug", KeyStyle.SCREAMING_SNAKE.fromCanonical("debug"));
  }

  @Test
  void dotsPreserved() {
    assertEquals("server.port",
      KeyStyle.KEBAB_CASE.fromCanonical("server.port"));
    assertEquals("time-zone.notifications",
      KeyStyle.KEBAB_CASE.fromCanonical("timeZone.notifications"));
  }

  @Test
  void noneIdentity() {
    assertEquals("anythingAtAll",
      KeyStyle.NONE.fromCanonical("anythingAtAll"));
    assertEquals("any-style_here",
      KeyStyle.NONE.toCanonical("any-style_here"));
  }

  @Test
  void roundTrip() {
    final String original = "daySyncModeWithLotsOfWords";
    for (final KeyStyle style : KeyStyle.values()) {
      if (KeyStyle.NONE == style) {
        continue;
      }
      assertEquals(original,
        style.toCanonical(style.fromCanonical(original)),
        "round-trip failed for " + style);
    }
  }
}