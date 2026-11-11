package io.github.vthattey.twinrail.core.rules;

/** How a failed rule is treated. */
public enum Severity {
  /** The message is rejected. */
  ERROR,
  /** The message is accepted but the problem is reported (for example to the inspector). */
  WARNING
}
