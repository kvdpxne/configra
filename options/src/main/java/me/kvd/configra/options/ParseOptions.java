package me.kvd.configra.options;

import java.util.Objects;
import me.kvd.configra.CommentChars;
import me.kvd.configra.DuplicatePolicy;
import me.kvd.configra.DuplicateSectionPolicy;
import me.kvd.configra.KeyStyle;

/**
 * Controls how a config file is parsed.
 *
 * <p>Options are grouped into four sub-records so that adding a new toggle
 * touches exactly one group. Presets and the builder expose flat setters
 * for ergonomics; the canonical representation stays grouped.</p>
 */
public record ParseOptions(
  WhitespaceOptions whitespace,
  CommentOptions comments,
  DuplicatePolicy duplicatePolicy,
  DuplicateSectionPolicy duplicateSectionPolicy,
  CaseOptions caseOptions,
  ValueOptions values,
  KeyStyle keyStyle
) {

  public ParseOptions {
    Objects.requireNonNull(whitespace, "whitespace");
    Objects.requireNonNull(comments, "comments");
    Objects.requireNonNull(duplicatePolicy, "duplicatePolicy");
    Objects.requireNonNull(duplicateSectionPolicy, "duplicateSectionPolicy");
    Objects.requireNonNull(caseOptions, "caseOptions");
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(keyStyle, "keyStyle");
  }

  // ----- Presets -----

  /**
   * Spec-literal defaults for {@code .properties}.
   */
  public static ParseOptions defaults() {
    return builder().build();
  }

  public static ParseOptions trimTrailing() {
    return builder().whitespace(WhitespaceOptions.TRIM).build();
  }

  public static ParseOptions lenientWhitespace() {
    return builder().whitespace(WhitespaceOptions.LENIENT).build();
  }

  /**
   * Recommended preset for {@code .ini} files.
   */
  public static ParseOptions iniDefaults() {
    return builder()
      .whitespace(WhitespaceOptions.TRIM)
      .comments(CommentOptions.INI)
      .build();
  }

  /**
   * Everything on: maximum tolerance, for migration tooling.
   */
  public static ParseOptions lenientAll() {
    return builder()
      .whitespace(new WhitespaceOptions(true, false))
      .comments(new CommentOptions(
        CommentChars.HASH_AND_SEMICOLON, true, true, true))
      .caseOptions(CaseOptions.WINDOWS)
      .values(new ValueOptions(true, true, true))
      .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  // ----- Builder -----

  public static final class Builder {

    private WhitespaceOptions whitespace = WhitespaceOptions.DEFAULT;
    private CommentOptions comments = CommentOptions.PROPERTIES;
    private DuplicatePolicy duplicatePolicy = DuplicatePolicy.LAST_WINS;
    private DuplicateSectionPolicy duplicateSectionPolicy =
      DuplicateSectionPolicy.MERGE;
    private CaseOptions caseOptions = CaseOptions.DEFAULT;
    private ValueOptions values = ValueOptions.DEFAULT;
    private KeyStyle keyStyle = KeyStyle.NONE;

    // ----- Group setters (preferred) -----

    public Builder whitespace(final WhitespaceOptions value) {
      this.whitespace = Objects.requireNonNull(value, "whitespace");
      return this;
    }

    public Builder comments(final CommentOptions value) {
      this.comments = Objects.requireNonNull(value, "comments");
      return this;
    }

    public Builder caseOptions(final CaseOptions value) {
      this.caseOptions = Objects.requireNonNull(value, "caseOptions");
      return this;
    }

    public Builder values(final ValueOptions value) {
      this.values = Objects.requireNonNull(value, "values");
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

    public Builder keyStyle(final KeyStyle value) {
      this.keyStyle = Objects.requireNonNull(value, "keyStyle");
      return this;
    }

    // ----- Flat setters (backward compatible, rebuild the sub-record) -----

    public Builder trimTrailingWhitespace(final boolean value) {
      this.whitespace = new WhitespaceOptions(
        value, this.whitespace.strictDelimiter());
      return this;
    }

    public Builder strictDelimiterWhitespace(final boolean value) {
      this.whitespace = new WhitespaceOptions(
        this.whitespace.trimTrailing(), value);
      return this;
    }

    public Builder commentChars(final CommentChars value) {
      this.comments = new CommentOptions(value,
        this.comments.spaceAfter(),
        this.comments.inline(),
        this.comments.parseDisabled());
      return this;
    }

    public Builder spaceAfterComment(final boolean value) {
      this.comments = new CommentOptions(this.comments.chars(), value,
        this.comments.inline(), this.comments.parseDisabled());
      return this;
    }

    public Builder inlineComments(final boolean value) {
      this.comments = new CommentOptions(this.comments.chars(),
        this.comments.spaceAfter(), value,
        this.comments.parseDisabled());
      return this;
    }

    public Builder parseDisabledProperties(final boolean value) {
      this.comments = new CommentOptions(this.comments.chars(),
        this.comments.spaceAfter(), this.comments.inline(), value);
      return this;
    }

    public Builder caseInsensitiveKeys(final boolean value) {
      this.caseOptions = new CaseOptions(value,
        this.caseOptions.insensitiveSections());
      return this;
    }

    public Builder caseInsensitiveSections(final boolean value) {
      this.caseOptions = new CaseOptions(
        this.caseOptions.insensitiveKeys(), value);
      return this;
    }

    public Builder unquoteValues(final boolean value) {
      this.values = new ValueOptions(value,
        this.values.relativeNesting(),
        this.values.indentedContinuation());
      return this;
    }

    public Builder relativeNesting(final boolean value) {
      this.values = new ValueOptions(this.values.unquote(), value,
        this.values.indentedContinuation());
      return this;
    }

    public Builder indentedContinuation(final boolean value) {
      this.values = new ValueOptions(this.values.unquote(),
        this.values.relativeNesting(), value);
      return this;
    }

    public ParseOptions build() {
      return new ParseOptions(
        this.whitespace,
        this.comments,
        this.duplicatePolicy,
        this.duplicateSectionPolicy,
        this.caseOptions,
        this.values,
        this.keyStyle);
    }
  }
}