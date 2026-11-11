package io.github.vthattey.twinrail.core.scenario;

import java.time.Duration;
import java.util.Map;

/**
 * What the emulator does when a scenario matches. Which actions apply depends on the rail: reject,
 * delay and drop act on ISO 20022 payments; return and noc act on ACH entries (see ADR 0009 and ADR
 * 0011). A recall action arrives only if a public spec covers it.
 */
public sealed interface Action {
  /** Reject the message with a reason code. */
  record Reject(String reason, String text) implements Action {}

  /** Answer after the given amount of virtual time. */
  record Delay(Duration by) implements Action {}

  /** Never answer, so the sender's time-out fires. */
  record Drop() implements Action {}

  /**
   * Return an ACH entry with a return code ({@code R01}...), {@code after} the entry settles. A
   * zero {@code after} returns it at once.
   */
  record Return(String code, Duration after, String text) implements Action {}

  /**
   * Notify the originator of a change ({@code C01}...) for a settled ACH entry. {@code corrected}
   * holds the corrected values the change code needs: account, routing, transactionCode,
   * individualId.
   */
  record Noc(String code, Map<String, String> corrected) implements Action {
    public Noc {
      // Insertion order is kept: Map.copyOf would reorder entries differently from one JVM run to
      // the next.
      corrected = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(corrected));
    }
  }
}
