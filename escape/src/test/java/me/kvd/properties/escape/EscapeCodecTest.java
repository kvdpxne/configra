package me.kvd.properties.escape;

import me.kvd.properties.exceptions.PropertyParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EscapeCodecTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "a\\nb   | a<NL>b",
    "a\\tb   | a<TAB>b",
    "a\\rb   | a<CR>b",
    "a\\fb   | a<FF>b",
    "a\\\\b  | a<BS>b",
    "a\\qb   | aqb",
  })
  void unescapeSequences(final String input, final String expected) {
    final String substituted = expected
      .replace("<NL>", "\n")
      .replace("<TAB>", "\t")
      .replace("<CR>", "\r")
      .replace("<FF>", "\f")
      .replace("<BS>", "\\");
    assertEquals(substituted, EscapeCodec.unescape(input));
  }

  @Test
  void unicodeEscape() {
    assertEquals("A", EscapeCodec.unescape("\\u0041"));
    assertEquals("こんにちは",
      EscapeCodec.unescape("\\u3053\\u3093\\u306b\\u3061\\u306f"));
  }

  @Test
  void hexEscapeTwoDigits() {
    // \x reads up to four hex digits; "\x41" = 0x41 = 'A'.
    assertEquals("A", EscapeCodec.unescape("\\x41"));
  }

  @Test
  void hexEscapeFourDigits() {
    // "\x0041" = 0x0041 = 'A' (leading zeroes do not change the code).
    assertEquals("A", EscapeCodec.unescape("\\x0041"));
  }

  @Test
  void hexEscapeStopsAtFourDigits() {
    // "\x000041" is parsed as \x0000 (null char) followed by "41".
    // The escape reads exactly four digits, then the rest is literal.
    assertEquals("\u000041", EscapeCodec.unescape("\\x000041"));
  }

  @Test
  void invalidUnicodeThrows() {
    assertThrows(PropertyParseException.class,
      () -> EscapeCodec.unescape("\\uZZZZ"));
    assertThrows(PropertyParseException.class,
      () -> EscapeCodec.unescape("\\u12"));
  }

  @Test
  void invalidHexThrows() {
    assertThrows(PropertyParseException.class,
      () -> EscapeCodec.unescape("\\x"));
  }

  @Test
  void noEscapeNoAllocation() {
    final String plain = "hello world";
    assertSame(plain, EscapeCodec.unescape(plain));
  }

  @Test
  void escapeKeyNothingNeeded() {
    final String key = "plainKey";
    assertSame(key, EscapeCodec.escapeKey(key, false));
  }

  @Test
  void escapeKeyNeeded() {
    assertEquals("key\\=with\\=equals",
      EscapeCodec.escapeKey("key=with=equals", false));
    assertEquals("key\\:colon",
      EscapeCodec.escapeKey("key:colon", false));
    assertEquals("key\\ with\\ spaces",
      EscapeCodec.escapeKey("key with spaces", false));
  }

  @Test
  void escapeValueLeadingSpace() {
    assertEquals("\\ leading",
      EscapeCodec.escapeValue(" leading", false));
  }

  @Test
  void escapeValueTrailingSpaceNotEscaped() {
    assertEquals("trailing ",
      EscapeCodec.escapeValue("trailing ", false));
  }

  @Test
  void escapeNonAsciiLeavesAsciiAlone() {
    // ASCII characters (<= 0x7F) are never escaped, even with the flag.
    assertEquals("A", EscapeCodec.escapeValue("A", true));
    assertEquals("hello", EscapeCodec.escapeValue("hello", true));
    assertEquals("0123", EscapeCodec.escapeValue("0123", true));
  }

  @Test
  void escapeNonAsciiEscapesAccentedCharacters() {
    // é = U+00E9, above the ASCII boundary.
    assertEquals("\\u00E9", EscapeCodec.escapeValue("é", true));
  }

  @Test
  void escapeNonAsciiEscapesCjk() {
    // こ = U+3053, above the ASCII boundary.
    assertEquals("\\u3053", EscapeCodec.escapeValue("こ", true));
  }

  @Test
  void escapeNonAsciiPreservesAsciiMix() {
    // Mixed: only the non-ASCII character gets the \\uHHHH form.
    assertEquals("caf\\u00E9",
      EscapeCodec.escapeValue("café", true));
  }

  @Test
  void roundTripAllSequences() {
    final String original = "line1\nline2\ttabbed \u00e9 backslash\\ here";
    final String escaped = EscapeCodec.escapeValue(original, true);
    assertEquals(original, EscapeCodec.unescape(escaped));
  }
}