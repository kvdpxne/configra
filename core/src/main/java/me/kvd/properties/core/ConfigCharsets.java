package me.kvd.properties.core;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Byte-to-text helpers used by {@link ConfigFileIO}. Centralises the BOM
 * handling and the UTF-8/ISO-8859-1 fallback so callers do not reimplement
 * the logic for each overload.
 */
final class ConfigCharsets {

    private ConfigCharsets() {
        throw new AssertionError("No instances.");
    }

    /**
     * Decodes {@code bytes} as UTF-8. On malformed input, falls back to
     * ISO-8859-1 (which never fails). A leading UTF-8 BOM is stripped.
     */
    static String decodeLenient(final byte[] bytes) {
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
     * UTF-8 BOM when the charset is UTF-8.
     */
    static String decodeWith(final byte[] bytes, final Charset charset) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(charset, "charset");
        final int offset = hasUtf8Bom(bytes) ? 3 : 0;
        return new String(bytes, offset, bytes.length - offset, charset);
    }

    static boolean hasUtf8Bom(final byte[] bytes) {
        return 3 <= bytes.length
                && (byte) 0xEF == bytes[0]
                && (byte) 0xBB == bytes[1]
                && (byte) 0xBF == bytes[2];
    }

    /**
     * Opens {@code source} for reading with a UTF-8 decoder that replaces
     * malformed input instead of throwing. Used by streaming APIs so they
     * do not diverge from {@code load} on decoding errors.
     */
    static Reader newLenientUtf8Reader(final Path source) throws IOException {
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return new InputStreamReader(Files.newInputStream(source), decoder);
    }
}