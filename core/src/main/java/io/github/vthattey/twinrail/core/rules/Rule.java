package io.github.vthattey.twinrail.core.rules;

import io.github.vthattey.twinrail.core.matcher.FieldMatcher;
import java.util.List;

/**
 * One check on one message type.
 *
 * @param message the message identifier this rule applies to, such as {@code pacs.008.001.08}
 * @param when conditions that must all hold for the rule to apply; empty means always
 * @param assertion what must be true when the rule applies
 * @param reason the reason code reported on failure (for example an ISO 20022 external status
 *     reason code)
 * @param text what is wrong, in plain words
 */
public record Rule(
    String id,
    String message,
    Source source,
    List<FieldMatcher> when,
    FieldMatcher assertion,
    String reason,
    String text,
    Severity severity) {
  public Rule {
    when = List.copyOf(when);
  }
}
