package me.kvd.properties.options;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import me.kvd.properties.CommentChars;
import me.kvd.properties.KeyStyle;

/**
 * Controls how a config file is serialised.
 */
public record WriteOptions(
  Charset charset,
  boolean escapeNonAscii,
  String lineSeparator,
  boolean atomic,
  boolean strictThread,
  CommentChars commentChars,
  char commentChar,
  boolean spaceAfterComment,
  boolean quoteValues,
  boolean normalizeComments,
  KeyStyle keyStyle
) {

  public WriteOptions {
    Objects.requireNonNull(charset, "charset");
    Objects.requireNonNull(lineSeparator, "lineSeparator");
    Objects.requireNonNull(commentChars, "commentChars");
    Objects.requireNonNull(keyStyle, "keyStyle");
    if (lineSeparator.isEmpty()) {
      throw new IllegalArgumentException("lineSeparator must not be empty");
    }
    if (!commentChars.accepts(commentChar)) {
      throw new IllegalArgumentException(
        "commentChar must be one of commentChars");
    }
  }

  public static WriteOptions utf8() {
    return new WriteOptions(
      StandardCharsets.UTF_8, false, "\n", true, false,
      CommentChars.HASH_AND_BANG, '#', true, false, false,
      KeyStyle.NONE);
  }

  public static WriteOptions ini() {
    return new WriteOptions(
      StandardCharsets.UTF_8, false, "\n", true, false,
      CommentChars.SEMICOLON_AND_HASH, ';', true, false, true,
      KeyStyle.NONE);
  }

  public static WriteOptions javaCompatible() {
    return new WriteOptions(
      StandardCharsets.ISO_8859_1, true,
      System.lineSeparator(), false, false,
      CommentChars.HASH, '#', true, false, false,
      KeyStyle.NONE);
  }

  public static WriteOptions isoLatin1() {
    return new WriteOptions(
      StandardCharsets.ISO_8859_1, true, "\n", true, false,
      CommentChars.HASH_AND_BANG, '#', true, false, false,
      KeyStyle.NONE);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private Charset charset = StandardCharsets.UTF_8;
    private boolean escapeNonAscii = false;
    private String lineSeparator = "\n";
    private boolean atomic = true;
    private boolean strictThread = false;
    private CommentChars commentChars = CommentChars.HASH_AND_BANG;
    private char commentChar = '#';
    private boolean spaceAfterComment = true;
    private boolean quoteValues = false;
    private boolean normalizeComments = false;
    private KeyStyle keyStyle = KeyStyle.NONE;

    public Builder charset(final Charset value) {
      this.charset = Objects.requireNonNull(value, "charset");
      return this;
    }

    public Builder escapeNonAscii(final boolean value) {
      this.escapeNonAscii = value;
      return this;
    }

    public Builder lineSeparator(final String value) {
      Objects.requireNonNull(value, "lineSeparator");
      if (value.isEmpty()) {
        throw new IllegalArgumentException(
          "lineSeparator must not be empty");
      }
      this.lineSeparator = value;
      return this;
    }

    public Builder atomic(final boolean value) {
      this.atomic = value;
      return this;
    }

    public Builder strictThread(final boolean value) {
      this.strictThread = value;
      return this;
    }

    public Builder commentChars(final CommentChars value) {
      this.commentChars = Objects.requireNonNull(value, "commentChars");
      return this;
    }

    public Builder commentChar(final char value) {
      this.commentChar = value;
      return this;
    }

    public Builder spaceAfterComment(final boolean value) {
      this.spaceAfterComment = value;
      return this;
    }

    public Builder quoteValues(final boolean value) {
      this.quoteValues = value;
      return this;
    }

    public Builder normalizeComments(final boolean value) {
      this.normalizeComments = value;
      return this;
    }

    public Builder keyStyle(final KeyStyle value) {
      this.keyStyle = Objects.requireNonNull(value, "keyStyle");
      return this;
    }

    public WriteOptions build() {
      return new WriteOptions(
        this.charset, this.escapeNonAscii, this.lineSeparator,
        this.atomic, this.strictThread,
        this.commentChars, this.commentChar,
        this.spaceAfterComment, this.quoteValues,
        this.normalizeComments, this.keyStyle);
    }
  }
}