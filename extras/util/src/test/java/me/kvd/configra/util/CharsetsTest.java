package me.kvd.configra.util;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharsetsTest {

  private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  @Test
  void detectsUtf8Bom() {
    assertTrue(Charsets.hasUtf8Bom(new byte[]{
      (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'A'}));
    assertFalse(Charsets.hasUtf8Bom("ABC".getBytes(StandardCharsets.UTF_8)));
    assertFalse(Charsets.hasUtf8Bom(new byte[]{(byte) 0xEF}));
    assertFalse(Charsets.hasUtf8Bom(new byte[0]));
  }

  @Test
  void decodeLenientStripsBom() {
    final byte[] input = new byte[4];
    System.arraycopy(BOM, 0, input, 0, 3);
    input[3] = 'A';
    assertEquals("A", Charsets.decodeLenient(input));
  }

  @Test
  void decodeLenientValidUtf8() {
    assertEquals("hello",
      Charsets.decodeLenient("hello".getBytes(StandardCharsets.UTF_8)));
    assertEquals("こんにちは",
      Charsets.decodeLenient("こんにちは".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void decodeLenientFallsBackToLatin1() {
    // 0xFF is invalid in UTF-8, valid Latin-1 (ÿ)
    final byte[] input = {(byte) 0xFF, (byte) 0xFE};
    final String result = Charsets.decodeLenient(input);
    assertEquals("\u00FF\u00FE", result);
  }

  @Test
  void decodeWithExplicitUtf8() {
    final byte[] input = new byte[4];
    System.arraycopy(BOM, 0, input, 0, 3);
    input[3] = 'A';
    assertEquals("A",
      Charsets.decodeWith(input, StandardCharsets.UTF_8));
  }

  @Test
  void decodeWithExplicitLatin1KeepsBom() {
    final byte[] input = new byte[4];
    System.arraycopy(BOM, 0, input, 0, 3);
    input[3] = 'A';
    // BOM bytes as Latin-1 characters, no strip
    assertEquals("\u00EF\u00BB\u00BFA",
      Charsets.decodeWith(input, StandardCharsets.ISO_8859_1));
  }
}