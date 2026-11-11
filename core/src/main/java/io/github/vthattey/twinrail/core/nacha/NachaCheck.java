package io.github.vthattey.twinrail.core.nacha;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks what an ACH file says about itself against what it contains: control counts, entry hashes,
 * totals, block count, padding and the fixed header values. Returns every problem found, each with
 * a stable id, so a rejection can name it.
 */
public final class NachaCheck {
  private NachaCheck() {}

  /**
   * One thing wrong with a file.
   *
   * @param id stable identifier, for example {@code nacha.batch.total-credit}
   * @param line the line it concerns, or 0 if it concerns the file as a whole
   */
  public record Problem(String id, int line, String text) {}

  public static List<Problem> verify(NachaFile file) {
    List<Problem> problems = new ArrayList<>();
    NachaRecord h = file.header();
    expect(problems, "nacha.header.record-size", h, "recordSize", "094");
    expect(problems, "nacha.header.blocking-factor", h, "blockingFactor", "10");
    expect(problems, "nacha.header.format-code", h, "formatCode", "1");

    long fileCount = 0;
    long fileHash = 0;
    long fileDebit = 0;
    long fileCredit = 0;
    for (NachaFile.Batch batch : file.batches()) {
      NachaRecord bh = batch.header();
      NachaRecord bc = batch.control();
      for (String field : List.of("serviceClassCode", "companyId", "odfiId")) {
        if (!bh.get(field).equals(bc.get(field))) {
          problems.add(
              new Problem(
                  "nacha.batch.control-mismatch",
                  bc.line(),
                  "Batch control "
                      + field
                      + " '"
                      + bc.get(field)
                      + "' differs from the batch header's '"
                      + bh.get(field)
                      + "'."));
        }
      }
      if (bh.number("batchNumber") != bc.number("batchNumber")) {
        problems.add(
            new Problem(
                "nacha.batch.control-mismatch",
                bc.line(),
                "Batch control batch number "
                    + bc.number("batchNumber")
                    + " differs from the batch header's "
                    + bh.number("batchNumber")
                    + "."));
      }

      long count = 0;
      long hash = 0;
      long debit = 0;
      long credit = 0;
      for (NachaFile.Entry e : batch.entries()) {
        NachaRecord d = e.detail();
        String code = d.get("transactionCode");
        count += 1 + e.addenda().size();
        hash = NachaAmounts.addHash(hash, d.number("rdfiId"));
        if (NachaAmounts.isCredit(code)) {
          credit += d.number("amount");
        } else if (NachaAmounts.isDebit(code)) {
          debit += d.number("amount");
        } else {
          problems.add(
              new Problem(
                  "nacha.entry.transaction-code",
                  d.line(),
                  "Transaction code '"
                      + code
                      + "' is not one Twinrail knows (22, 23, 27, 28, 32, 33, 37, 38);"
                      + " its amount is left out of the totals."));
        }
        boolean flagged = d.get("addendaIndicator").equals("1");
        if (flagged != !e.addenda().isEmpty()) {
          problems.add(
              new Problem(
                  "nacha.entry.addenda-indicator",
                  d.line(),
                  "Addenda indicator is '"
                      + d.get("addendaIndicator")
                      + "' but the entry has "
                      + e.addenda().size()
                      + " addenda record(s)."));
        }
      }
      compare(
          problems,
          "nacha.batch.count",
          "entry and addenda count",
          count,
          bc.number("entryAddendaCount"),
          bc.line());
      compare(problems, "nacha.batch.hash", "entry hash", hash, bc.number("entryHash"), bc.line());
      compare(
          problems,
          "nacha.batch.total-debit",
          "total debit",
          debit,
          bc.number("totalDebit"),
          bc.line());
      compare(
          problems,
          "nacha.batch.total-credit",
          "total credit",
          credit,
          bc.number("totalCredit"),
          bc.line());
      fileCount += count;
      fileHash = NachaAmounts.addHash(fileHash, hash);
      fileDebit += debit;
      fileCredit += credit;
    }

    NachaRecord fc = file.control();
    int lines = file.records().size() + file.paddingLines();
    compare(
        problems,
        "nacha.file.batch-count",
        "batch count",
        file.batches().size(),
        fc.number("batchCount"),
        fc.line());
    compare(
        problems,
        "nacha.file.count",
        "entry and addenda count",
        fileCount,
        fc.number("entryAddendaCount"),
        fc.line());
    compare(problems, "nacha.file.hash", "entry hash", fileHash, fc.number("entryHash"), fc.line());
    compare(
        problems,
        "nacha.file.total-debit",
        "total debit",
        fileDebit,
        fc.number("totalDebit"),
        fc.line());
    compare(
        problems,
        "nacha.file.total-credit",
        "total credit",
        fileCredit,
        fc.number("totalCredit"),
        fc.line());
    if (lines % 10 != 0) {
      problems.add(
          new Problem(
              "nacha.file.padding",
              0,
              "The file has "
                  + lines
                  + " lines; the count must be a multiple of 10. Pad the last block with"
                  + " lines of 94 nines."));
    }
    compare(
        problems,
        "nacha.file.block-count",
        "block count",
        lines / 10,
        fc.number("blockCount"),
        fc.line());
    return problems;
  }

  private static void expect(
      List<Problem> problems, String id, NachaRecord r, String field, String want) {
    if (!r.get(field).equals(want)) {
      problems.add(
          new Problem(
              id,
              r.line(),
              "File header " + field + " is '" + r.get(field) + "' but must be '" + want + "'."));
    }
  }

  private static void compare(
      List<Problem> problems, String id, String what, long actual, long stated, int line) {
    if (actual != stated) {
      problems.add(
          new Problem(
              id,
              line,
              "The control record states "
                  + what
                  + " "
                  + stated
                  + " but the records add up to "
                  + actual
                  + "."));
    }
  }
}
