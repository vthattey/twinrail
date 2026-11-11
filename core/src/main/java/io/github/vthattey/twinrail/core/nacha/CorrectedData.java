package io.github.vthattey.twinrail.core.nacha;

import java.util.Map;

/**
 * Lays out the 29-character corrected-data field of a notification-of-change addenda record for the
 * change codes whose layout is documented in the sources (docs/research.md section 7): a single
 * value is left-justified; account, routing and transaction code combine with the transaction code
 * (and for C03 the account) ending at the last position.
 */
final class CorrectedData {
  static final int WIDTH = 29;

  private CorrectedData() {}

  static String format(String changeCode, Map<String, String> v) {
    return switch (changeCode) {
      case "C01" -> single(v.get("account"));
      case "C02" -> single(v.get("routing"));
      case "C03" -> endingWith(v.get("routing"), v.get("account"));
      case "C05" -> single(v.get("transactionCode"));
      case "C06" -> endingWith(v.get("account"), v.get("transactionCode"));
      case "C07" -> endingWith(v.get("routing") + v.get("account"), v.get("transactionCode"));
      case "C09" -> single(v.get("individualId"));
      default ->
          throw new NachaException(
              "Change code " + changeCode + " has no corrected-data layout in Twinrail yet.");
    };
  }

  private static String single(String value) {
    check(value, value);
    return value;
  }

  /** {@code start} at the left, {@code end} right-justified in the last positions. */
  private static String endingWith(String start, String end) {
    check(start + end, start + "/" + end);
    return start + " ".repeat(WIDTH - start.length() - end.length()) + end;
  }

  private static void check(String joined, String shown) {
    if (joined.length() > WIDTH) {
      throw new NachaException(
          "The corrected values '"
              + shown
              + "' need "
              + joined.length()
              + " characters but the corrected-data field holds "
              + WIDTH
              + ". Shorten them.");
    }
  }
}
