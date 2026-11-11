package io.github.vthattey.twinrail.core.nacha;

import java.util.Set;

/**
 * Which side of the money an entry's transaction code is on. Only the codes the research sources
 * list are known (docs/research.md section 7): 22 and 32 credits, 27 and 37 debits, and the
 * zero-dollar prenote codes 23, 33 (credit side) and 28, 38 (debit side).
 */
final class NachaAmounts {
  private static final Set<String> CREDITS = Set.of("22", "23", "32", "33");
  private static final Set<String> DEBITS = Set.of("27", "28", "37", "38");

  private NachaAmounts() {}

  static boolean isCredit(String transactionCode) {
    return CREDITS.contains(transactionCode);
  }

  static boolean isDebit(String transactionCode) {
    return DEBITS.contains(transactionCode);
  }

  static boolean isKnown(String transactionCode) {
    return isCredit(transactionCode) || isDebit(transactionCode);
  }

  /** The sum of 8-digit receiving DFI ids, keeping the rightmost 10 digits. */
  static long addHash(long hash, long rdfiId) {
    return (hash + rdfiId) % 10_000_000_000L;
  }
}
