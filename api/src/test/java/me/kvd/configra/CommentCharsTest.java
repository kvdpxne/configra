package me.kvd.configra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentCharsTest {

  @Test
  void singleMarker() {
    assertTrue(CommentChars.HASH.matches('#'));
    assertFalse(CommentChars.HASH.matches('!'));
    assertFalse(CommentChars.HASH.matches(';'));
  }

  @Test
  void dualMarker() {
    assertTrue(CommentChars.HASH_AND_BANG.matches('#'));
    assertTrue(CommentChars.HASH_AND_BANG.matches('!'));
    assertFalse(CommentChars.HASH_AND_BANG.matches(';'));
    assertTrue(CommentChars.HASH_AND_BANG.isDual());
  }

  @Test
  void primaryAndSecondary() {
    assertEquals('#', CommentChars.HASH_AND_BANG.primary());
    assertEquals('!', CommentChars.HASH_AND_BANG.secondary());
    assertTrue(CommentChars.HASH_AND_BANG.isPrimary('#'));
    assertFalse(CommentChars.HASH_AND_BANG.isPrimary('!'));
  }

  @Test
  void rejectsNul() {
    assertThrows(IllegalArgumentException.class,
      () -> new CommentChars('\0', '\0'));
  }

  @Test
  void rejectsSameMarkers() {
    assertThrows(IllegalArgumentException.class,
      () -> new CommentChars('#', '#'));
  }

  @Test
  void acceptsAny() {
    assertTrue(CommentChars.SEMICOLON_AND_HASH.accepts(';'));
    assertTrue(CommentChars.SEMICOLON_AND_HASH.accepts('#'));
    assertFalse(CommentChars.SEMICOLON_AND_HASH.accepts('!'));
  }
}