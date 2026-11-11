package io.github.vthattey.twinrail.core.scenario;

import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import java.util.List;
import java.util.Optional;

/** An ordered list of scenarios. The first one that matches a message wins. */
public record ScenarioSet(List<Scenario> scenarios) {
  public ScenarioSet {
    scenarios = List.copyOf(scenarios);
  }

  public static ScenarioSet empty() {
    return new ScenarioSet(List.of());
  }

  public Optional<Scenario> firstMatch(XmlMessage message) {
    return scenarios.stream().filter(s -> s.matches(message)).findFirst();
  }
}
