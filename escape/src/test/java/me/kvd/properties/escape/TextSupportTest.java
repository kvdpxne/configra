package me.kvd.properties.escape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSupportTest {

  @Test
  void unquoteDouble() {
    assertEquals("hello", TextSupport.unquote("\"hello\""));
  }

  @Test
  void unquoteSingle() {
    assertEquals("hello", TextSupport.unquote("'hello'"));
  }

  @Test
  void unquoteNoQuotes() {
    assertEquals("hello", TextSupport.unquote("hello"));
  }

  @Test
  void unquoteMismatched() {
    assertEquals("\"hello'", TextSupport.unquote("\"hello'"));
  }

  @Test
  void unquoteTooShort() {
    assertEquals("", TextSupport.unquote(""));
    assertEquals("a", TextSupport.unquote("a"));
  }

  @Test
  void normaliseLf() {
    assertEquals("a\nb", TextSupport.normaliseLineEndings("a\nb"));
  }

  @Test
  void normaliseCrLf() {
    assertEquals("a\nb", TextSupport.normaliseLineEndings("a\r\nb"));
  }

  @Test
  void normaliseLoneCr() {
    assertEquals("a\nb", TextSupport.normaliseLineEndings("a\rb"));
  }

  @Test
  void endsWithUnescapedBackslash() {
    assertTrue(TextSupport.endsWithUnescapedBackslash("abc\\"));
    assertFalse(TextSupport.endsWithUnescapedBackslash("abc"));
    assertFalse(TextSupport.endsWithUnescapedBackslash("abc\\\\"));
    assertTrue(TextSupport.endsWithUnescapedBackslash("abc\\\\\\"));
  }
}