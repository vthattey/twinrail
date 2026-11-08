package io.github.vthattey.twinrail.core.rules;

/**
 * A rule that a message failed. A rejection always names the rule and the reason code.
 *
 * @param path the path the failing check looked at
 * @param expectation what the rule required, for example {@code equals '1'}
 */
public record Violation(
    String ruleId,
    String reason,
    String text,
    Severity severity,
    Source source,
    String path,
    String expectation) {
  /** One line for logs and error responses. */
  public String summary() {
    return "Rule " + ruleId + " (reason " + reason + "): " + text;
  }
}
