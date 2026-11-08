package io.github.vthattey.twinrail.core.rules;

import java.util.List;
import java.util.Optional;

/** The outcome of checking one message against a rule pack. */
public record Evaluation(List<Violation> violations) {
  public Evaluation {
    violations = List.copyOf(violations);
  }

  /** True if any error-severity rule failed. */
  public boolean rejected() {
    return firstError().isPresent();
  }

  /** The first failed error-severity rule in pack order; this is the one a rejection reports. */
  public Optional<Violation> firstError() {
    return violations.stream().filter(v -> v.severity() == Severity.ERROR).findFirst();
  }
}
