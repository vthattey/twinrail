package io.github.vthattey.twinrail.core.lifecycle;

/** Where a payment is in its life. ACCEPTED is the only state that can still change. */
public enum PaymentState {
  /** Passed the rules and scenarios; money is reserved and settlement or time-out is pending. */
  ACCEPTED,
  /** Booked on the ledger. Final. */
  SETTLED,
  /** Refused with a rule id and reason code. Final. */
  REJECTED,
  /** Never settled before its time-out on the virtual clock. Final. */
  TIMED_OUT;

  public boolean isFinal() {
    return this != ACCEPTED;
  }
}
