package me.kvdpxne.properties;

import java.util.Objects;

/**
 * Controls how a config file is parsed.
 *
 * <p>Every flag has a defensive default that matches the {@code
 * java.util.Properties} / core-INI behaviour. Opt in for dialect quirks.</p>
 */
public record ParseOptions(
  boolean trimTrailingWhitespace,
  boolean strictDelimiterWhitespace,
  CommentChars commentChars,
  boolean spaceAfterComment,
  DuplicatePolicy duplicatePolicy,
  DuplicateSectionPolicy duplicateSectionPolicy,
  boolean caseInsensitiveKeys,
  boolean caseInsensitiveSections,
  boolean unquoteValues,
  boolean inlineComments,
  boolean relativeNesting,
  boolean indentedContinuation,
  boolean parseDisabledProperties,
  KeyStyle keyStyle
) {

  public ParseOptions {
    Objects.requireNonNull(commentChars, "commentChars");
    Objects.requireNonNull(duplicatePolicy, "duplicatePolicy");
    Objects.requireNonNull(duplicateSectionPolicy, "duplicateSectionPolicy");
    Objects.requireNonNull(keyStyle, "keyStyle");
  }

  // ----- Presets -----

  /**
   * Spec-literal defaults for {@code .properties}.
   */
  public static ParseOptions defaults() {
    return new ParseOptions(
      false, true, CommentChars.HASH_AND_BANG, true,
      DuplicatePolicy.LAST_WINS, DuplicateSectionPolicy.MERGE,
      false, false, false, false, false, false, false,
      KeyStyle.NONE);
  }

  public static ParseOptions trimTrailing() {
    return new ParseOptions(
      true, true,
      CommentChars.HASH_AND_BANG, true,
      DuplicatePolicy.LAST_WINS, DuplicateSectionPolicy.MERGE,
      false, false, false, false, false, false, false,
      KeyStyle.NONE);
  }

  public static ParseOptions lenientWhitespace() {
    return new ParseOptions(
      false, false,
      CommentChars.HASH_AND_BANG, true,
      DuplicatePolicy.LAST_WINS, DuplicateSectionPolicy.MERGE,
      false, false, false, false, false, false, false,
      KeyStyle.NONE);
  }

  /**
   * Recommended preset for {@code .ini} files: accepts both {@code ;} and
   * {@code #} comments, matching Python {@code configparser} and most
   * Unix-style INI parsers.
   */
  public static ParseOptions iniDefaults() {
    return new ParseOptions(
      true, true, CommentChars.SEMICOLON_AND_HASH, false,
      DuplicatePolicy.LAST_WINS, DuplicateSectionPolicy.MERGE,
      false, false, false, false, false, false, false,
      KeyStyle.NONE);
  }

  /**
   * Everything on: maximum tolerance, useful for migration tooling.
   */
  public static ParseOptions lenientAll() {
    return new ParseOptions(
      true, false,
      CommentChars.HASH_AND_SEMICOLON, true,
      DuplicatePolicy.LAST_WINS, DuplicateSectionPolicy.MERGE,
      true, true, true, true, true, true, true,
      KeyStyle.NONE);
  }

  public static Builder builder() {
    return new Builder();
  }

// ----- Builder -----

  public static final class Builder {

    private boolean trimTrailingWhitespace = false;
    private boolean strictDelimiterWhitespace = true;
    private CommentChars commentChars = CommentChars.HASH_AND_BANG;
    private boolean spaceAfterComment = true;
    private DuplicatePolicy duplicatePolicy = DuplicatePolicy.LAST_WINS;
    private DuplicateSectionPolicy duplicateSectionPolicy =
      DuplicateSectionPolicy.MERGE;
    private boolean caseInsensitiveKeys = false;
    private boolean caseInsensitiveSections = false;
    private boolean unquoteValues = false;
    private boolean inlineComments = false;
    private boolean relativeNesting = false;
    private boolean indentedContinuation = false;
    private boolean parseDisabledProperties = false;
    private KeyStyle keyStyle = KeyStyle.NONE;

    public Builder trimTrailingWhitespace(final boolean value) {
      this.trimTrailingWhitespace = value;
      return this;
    }

    public Builder strictDelimiterWhitespace(final boolean value) {
      this.strictDelimiterWhitespace = value;
      return this;
    }

    public Builder commentChars(final CommentChars value) {
      this.commentChars = Objects.requireNonNull(value, "commentChars");
      return this;
    }

    public Builder spaceAfterComment(final boolean value) {
      this.spaceAfterComment = value;
      return this;
    }

    public Builder duplicatePolicy(final DuplicatePolicy value) {
      this.duplicatePolicy = Objects.requireNonNull(value, "duplicatePolicy");
      return this;
    }

    public Builder duplicateSectionPolicy(final DuplicateSectionPolicy value) {
      this.duplicateSectionPolicy =
        Objects.requireNonNull(value, "duplicateSectionPolicy");
      return this;
    }

    public Builder caseInsensitiveKeys(final boolean value) {
      this.caseInsensitiveKeys = value;
      return this;
    }

    public Builder caseInsensitiveSections(final boolean value) {
      this.caseInsensitiveSections = value;
      return this;
    }

    public Builder unquoteValues(final boolean value) {
      this.unquoteValues = value;
      return this;
    }

    public Builder inlineComments(final boolean value) {
      this.inlineComments = value;
      return this;
    }

    public Builder relativeNesting(final boolean value) {
      this.relativeNesting = value;
      return this;
    }

    public Builder indentedContinuation(final boolean value) {
      this.indentedContinuation = value;
      return this;
    }

    public Builder parseDisabledProperties(final boolean value) {
      this.parseDisabledProperties = value;
      return this;
    }

    public Builder keyStyle(final KeyStyle value) {
      this.keyStyle = Objects.requireNonNull(value, "keyStyle");
      return this;
    }

    public ParseOptions build() {
      return new ParseOptions(
        this.trimTrailingWhitespace,
        this.strictDelimiterWhitespace,
        this.commentChars,
        this.spaceAfterComment,
        this.duplicatePolicy,
        this.duplicateSectionPolicy,
        this.caseInsensitiveKeys,
        this.caseInsensitiveSections,
        this.unquoteValues,
        this.inlineComments,
        this.relativeNesting,
        this.indentedContinuation,
        this.parseDisabledProperties,
        this.keyStyle);
    }
  }
}