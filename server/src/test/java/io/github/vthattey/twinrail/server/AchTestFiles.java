package io.github.vthattey.twinrail.server;

import io.github.vthattey.twinrail.core.nacha.NachaBuilder;
import io.github.vthattey.twinrail.core.nacha.NachaCodec;
import java.util.Map;

/** Builds ACH files for the server tests. */
final class AchTestFiles {
  private AchTestFiles() {}

  /** Two credits: 1500.00 to account ACC-1 (JANE DOE) and 2125.50 to ACC-2 (JOHN ROE). */
  static String credits() {
    return NachaCodec.write(
        base()
            .entry(entry("22", "11111111", "8", "ACC-1", "150000", "JANE DOE", "123456780000001"))
            .entry(entry("22", "22222222", "6", "ACC-2", "212550", "JOHN ROE", "123456780000002"))
            .build());
  }

  private static NachaBuilder base() {
    return new NachaBuilder()
        .header(
            Map.of(
                "immediateDestination", " 987654320",
                "immediateOrigin", " 123456780",
                "fileCreationDate", "261110",
                "fileCreationTime", "1000",
                "fileIdModifier", "A",
                "destinationName", "DEST BANK",
                "originName", "ACME CORP"))
        .batch(
            Map.of(
                "serviceClassCode", "220",
                "companyName", "ACME CORP",
                "companyId", "1123456789",
                "secCode", "PPD",
                "entryDescription", "PAYROLL",
                "effectiveEntryDate", "261111",
                "originatorStatusCode", "1",
                "odfiId", "12345678",
                "batchNumber", "1"));
  }

  private static Map<String, String> entry(
      String txn,
      String rdfi,
      String check,
      String account,
      String cents,
      String name,
      String trace) {
    return Map.of(
        "transactionCode", txn,
        "rdfiId", rdfi,
        "checkDigit", check,
        "accountNumber", account,
        "amount", cents,
        "individualId", "ID",
        "individualName", name,
        "traceNumber", trace);
  }
}
