package io.github.vthattey.twinrail.core.ledger;

/** Which side of an account a booking is on. */
public enum Side {
  DEBIT("DBIT"),
  CREDIT("CRDT");

  private final String code;

  Side(String code) {
    this.code = code;
  }

  /** The ISO 20022 credit/debit indicator, {@code DBIT} or {@code CRDT}. */
  public String code() {
    return code;
  }
}
