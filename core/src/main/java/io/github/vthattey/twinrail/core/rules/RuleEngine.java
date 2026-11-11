package io.github.vthattey.twinrail.core.rules;

import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import java.util.ArrayList;
import java.util.List;

/** Checks a message against the rules of a pack. Stateless and thread-safe. */
public final class RuleEngine {
  /** Runs every rule for the message's type, in pack order, and collects the failures. */
  public Evaluation evaluate(RulePack pack, XmlMessage message) {
    List<Violation> violations = new ArrayList<>();
    for (Rule rule : pack.rulesFor(message.type())) {
      boolean applies = rule.when().stream().allMatch(w -> w.selects(message));
      if (applies && !rule.assertion().holds(message)) {
        violations.add(
            new Violation(
                rule.id(),
                rule.reason(),
                rule.text(),
                rule.severity(),
                rule.source(),
                rule.assertion().path().text(),
                rule.assertion().condition().describe()));
      }
    }
    return new Evaluation(violations);
  }
}
