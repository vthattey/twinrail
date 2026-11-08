package io.github.vthattey.twinrail.core.matcher;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A test applied to the values a path selects.
 *
 * <p>{@link Present} and {@link Absent} look at whether anything was selected. Every other
 * condition must hold for <em>every</em> selected value, and holds trivially when nothing was
 * selected (combine with {@link Present} to require the element).
 */
public sealed interface Condition {
  boolean test(List<String> values);

  /** Short wording for messages, for example {@code equals 'CAD'}. */
  String describe();

  record Present() implements Condition {
    @Override
    public boolean test(List<String> values) {
      return !values.isEmpty();
    }

    @Override
    public String describe() {
      return "is present";
    }
  }

  record Absent() implements Condition {
    @Override
    public boolean test(List<String> values) {
      return values.isEmpty();
    }

    @Override
    public String describe() {
      return "is absent";
    }
  }

  record Equals(String expected) implements Condition {
    @Override
    public boolean test(List<String> values) {
      return values.stream().allMatch(expected::equals);
    }

    @Override
    public String describe() {
      return "equals '" + expected + "'";
    }
  }

  record NotEquals(String unexpected) implements Condition {
    @Override
    public boolean test(List<String> values) {
      return values.stream().noneMatch(unexpected::equals);
    }

    @Override
    public String describe() {
      return "does not equal '" + unexpected + "'";
    }
  }

  record OneOf(List<String> allowed) implements Condition {
    public OneOf {
      allowed = List.copyOf(allowed);
    }

    @Override
    public boolean test(List<String> values) {
      return allowed.containsAll(values);
    }

    @Override
    public String describe() {
      return "is one of " + allowed;
    }
  }

  /** The whole value must match the pattern. */
  record Matches(Pattern pattern) implements Condition {
    @Override
    public boolean test(List<String> values) {
      return values.stream().allMatch(v -> pattern.matcher(v).matches());
    }

    @Override
    public String describe() {
      return "matches /" + pattern.pattern() + "/";
    }
  }

  record MaxLength(int max) implements Condition {
    @Override
    public boolean test(List<String> values) {
      return values.stream().allMatch(v -> v.length() <= max);
    }

    @Override
    public String describe() {
      return "has at most " + max + " characters";
    }
  }

  record MinLength(int min) implements Condition {
    @Override
    public boolean test(List<String> values) {
      return values.stream().allMatch(v -> v.length() >= min);
    }

    @Override
    public String describe() {
      return "has at least " + min + " characters";
    }
  }

  /** Numeric lower bound, inclusive. A value that is not a number fails. */
  record Min(BigDecimal min) implements Condition {
    @Override
    public boolean test(List<String> values) {
      return values.stream().allMatch(v -> number(v) != null && number(v).compareTo(min) >= 0);
    }

    @Override
    public String describe() {
      return "is at least " + min.toPlainString();
    }
  }

  /** Numeric upper bound, inclusive. A value that is not a number fails. */
  record Max(BigDecimal max) implements Condition {
    @Override
    public boolean test(List<String> values) {
      return values.stream().allMatch(v -> number(v) != null && number(v).compareTo(max) <= 0);
    }

    @Override
    public String describe() {
      return "is at most " + max.toPlainString();
    }
  }

  private static BigDecimal number(String text) {
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
