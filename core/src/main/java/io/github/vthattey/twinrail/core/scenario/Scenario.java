package io.github.vthattey.twinrail.core.scenario;

import io.github.vthattey.twinrail.core.matcher.FieldMatcher;
import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import java.util.List;

/**
 * Scripted behaviour for matching messages.
 *
 * @param message message identifier to match, or {@code null} for any
 * @param where field checks that must all hold; empty matches every message of that type
 */
public record Scenario(
    String id, String description, String message, List<FieldMatcher> where, Action action) {
  public Scenario {
    where = List.copyOf(where);
  }

  public boolean matches(XmlMessage candidate) {
    return (message == null || message.equals(candidate.type()))
        && where.stream().allMatch(m -> m.selects(candidate));
  }
}
