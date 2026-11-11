package io.github.vthattey.twinrail.core.rules;

import java.util.List;

/**
 * A versioned set of rules for one scheme or profile.
 *
 * @param version the pack version (not the specification version; each rule records that)
 */
public record RulePack(String id, String name, String version, List<Rule> rules) {
  public RulePack {
    rules = List.copyOf(rules);
  }

  /** Rules that apply to a message type, in file order. */
  public List<Rule> rulesFor(String messageType) {
    return rules.stream().filter(r -> r.message().equals(messageType)).toList();
  }
}
