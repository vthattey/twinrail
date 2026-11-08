package io.github.vthattey.twinrail.core.scenario;

import java.time.Duration;

/**
 * What the emulator does when a scenario matches. Return and recall actions arrive with the rails
 * that support them (see ADR 0009).
 */
public sealed interface Action {
  /** Reject the message with a reason code. */
  record Reject(String reason, String text) implements Action {}

  /** Answer after the given amount of virtual time. */
  record Delay(Duration by) implements Action {}

  /** Never answer, so the sender's time-out fires. */
  record Drop() implements Action {}
}
