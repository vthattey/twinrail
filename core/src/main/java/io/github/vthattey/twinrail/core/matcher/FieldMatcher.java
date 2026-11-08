package io.github.vthattey.twinrail.core.matcher;

import io.github.vthattey.twinrail.core.definition.DefinitionException;
import io.github.vthattey.twinrail.core.definition.Fields;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One check on one element path, written in YAML as {@code {path: ..., <condition>: <value>}} with
 * exactly one condition. Rule packs and scenario files use the same syntax.
 */
public record FieldMatcher(PathExpr path, Condition condition) {
  /** Condition keys, in the order shown in error messages. */
  public static final List<String> CONDITION_KEYS =
      List.of(
          "present",
          "absent",
          "equals",
          "notEquals",
          "oneOf",
          "matches",
          "maxLength",
          "minLength",
          "min",
          "max");

  /**
   * Assertion semantics: the condition must hold for every selected value, and holds trivially when
   * the element is missing (use {@code present: true} to require it). Used by rule {@code assert}.
   */
  public boolean holds(XmlMessage message) {
    return condition.test(message.values(path));
  }

  /**
   * Selector semantics: like {@link #holds} but a missing element never matches, except for {@code
   * absent: true}. Used by rule {@code when} and scenario {@code where}, so that "creditor equals
   * X" does not select messages that have no creditor.
   */
  public boolean selects(XmlMessage message) {
    var values = message.values(path);
    return condition.test(values) && (!values.isEmpty() || condition instanceof Condition.Absent);
  }

  /** Reads a matcher and checks the mapping has no unknown keys. */
  public static FieldMatcher read(Fields f) {
    PathExpr path = parsePath(f);
    List<String> present = new ArrayList<>();
    for (String key : CONDITION_KEYS) {
      if (f.has(key)) {
        present.add(key);
      }
    }
    if (present.size() != 1) {
      throw new DefinitionException(
          f.where()
              + " needs exactly one condition, found "
              + present.size()
              + ". Use one of: "
              + String.join(", ", CONDITION_KEYS)
              + ".");
    }
    String key = present.get(0);
    Condition condition = readCondition(f, key);
    f.finish();
    return new FieldMatcher(path, condition);
  }

  private static PathExpr parsePath(Fields f) {
    String text = f.requireString("path");
    try {
      return PathExpr.parse(text);
    } catch (DefinitionException e) {
      throw new DefinitionException(f.where() + ": " + e.getMessage(), e);
    }
  }

  private static Condition readCondition(Fields f, String key) {
    Object value = f.raw(key);
    return switch (key) {
      case "present" -> {
        requireTrue(f, key, value);
        yield new Condition.Present();
      }
      case "absent" -> {
        requireTrue(f, key, value);
        yield new Condition.Absent();
      }
      case "equals" -> new Condition.Equals(text(f, key, value));
      case "notEquals" -> new Condition.NotEquals(text(f, key, value));
      case "oneOf" -> new Condition.OneOf(texts(f, key, value));
      case "matches" -> new Condition.Matches(pattern(f, key, value));
      case "maxLength" -> new Condition.MaxLength(count(f, key, value));
      case "minLength" -> new Condition.MinLength(count(f, key, value));
      case "min" -> new Condition.Min(number(f, key, value));
      case "max" -> new Condition.Max(number(f, key, value));
      default -> throw new IllegalStateException(key);
    };
  }

  private static void requireTrue(Fields f, String key, Object value) {
    if (!Boolean.TRUE.equals(value)) {
      throw new DefinitionException(
          f.where()
              + ": '"
              + key
              + "' must be true, for example "
              + key
              + ": true. Use the other one of present/absent for the opposite.");
    }
  }

  private static String text(Fields f, String key, Object value) {
    if (!(value instanceof String text)) {
      throw new DefinitionException(
          f.where()
              + ": '"
              + key
              + "' must be quoted text, for example "
              + key
              + ": \"1.50\" (unquoted numbers lose trailing zeros).");
    }
    return text;
  }

  private static List<String> texts(Fields f, String key, Object value) {
    if (!(value instanceof List<?> list) || list.isEmpty()) {
      throw new DefinitionException(
          f.where() + ": '" + key + "' must be a non-empty list of quoted text values.");
    }
    List<String> out = new ArrayList<>();
    for (Object item : list) {
      out.add(text(f, key, item));
    }
    return out;
  }

  private static Pattern pattern(Fields f, String key, Object value) {
    String regex = text(f, key, value);
    try {
      return Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new DefinitionException(
          f.where() + ": '" + key + "' is not a valid regular expression: " + e.getDescription(),
          e);
    }
  }

  private static int count(Fields f, String key, Object value) {
    if (value instanceof Integer n && n >= 0) {
      return n;
    }
    throw new DefinitionException(f.where() + ": '" + key + "' must be a whole number, 0 or more.");
  }

  private static BigDecimal number(Fields f, String key, Object value) {
    try {
      if (value instanceof Integer || value instanceof Long) {
        return new BigDecimal(value.toString());
      }
      if (value instanceof String text) {
        return new BigDecimal(text);
      }
    } catch (NumberFormatException e) {
      // fall through to the shared message
    }
    throw new DefinitionException(
        f.where() + ": '" + key + "' must be a whole number or a quoted decimal such as \"0.01\".");
  }
}
