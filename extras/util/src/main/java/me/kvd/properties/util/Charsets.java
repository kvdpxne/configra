package me.kvd.properties.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Public charset helpers used across the library. Centralises UTF-8 BOM
 * handling and the lenient-decoding strategy (UTF-8 with an ISO-8859-1
 * fallback on malformed bytes) so consumers do not reimplement it.
 */
public final class Charsets {

  private Charsets() {
    throw new AssertionError("No instances.");
  }

  /**
   * Returns {@code true} when {@code bytes} starts with the UTF-8 BOM
   * sequence ({@code EF BB BF}).
   */
  public static boolean hasUtf8Bom(final byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    return 3 <= bytes.length
      && (byte) 0xEF == bytes[0]
      && (byte) 0xBB == bytes[1]
      && (byte) 0xBF == bytes[2];
  }

  /**
   * Decodes {@code bytes} as UTF-8, stripping a leading BOM. On malformed
   * input the entire buffer is re-decoded as ISO-8859-1 (which never
   * fails). This mirrors JDK 9+ behaviour for property resource bundles.
   */
  public static String decodeLenient(final byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    final int offset = hasUtf8Bom(bytes) ? 3 : 0;
    final int length = bytes.length - offset;
    try {
      final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
      return decoder.decode(ByteBuffer.wrap(bytes, offset, length))
        .toString();
    } catch (final CharacterCodingException ignored) {
      return new String(bytes, offset, length,
        StandardCharsets.ISO_8859_1);
    }
  }

  /**
   * Decodes {@code bytes} with an explicit charset, stripping a leading
   * UTF-8 BOM only when {@code charset} is UTF-8.
   */
  public static String decodeWith(final byte[] bytes, final Charset charset) {
    Objects.requireNonNull(bytes, "bytes");
    Objects.requireNonNull(charset, "charset");
    final boolean strip = StandardCharsets.UTF_8.equals(charset)
      && hasUtf8Bom(bytes);
    final int offset = strip ? 3 : 0;
    return new String(bytes, offset, bytes.length - offset, charset);
  }
}