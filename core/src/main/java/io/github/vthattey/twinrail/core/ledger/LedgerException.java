package io.github.vthattey.twinrail.core.ledger;

/** A ledger operation was refused. The message says what to change. */
public final class LedgerException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public LedgerException(String message) {
    super(message);
  }
}
