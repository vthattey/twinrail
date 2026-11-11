package io.github.vthattey.twinrail.core.nacha;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an ACH file in memory and fills in everything that is derived: the addenda indicator,
 * batch and file control records (counts, entry hash, totals, block count) and the fixed file
 * header values. Callers give field values as strings; amounts are in cents.
 */
public final class NachaBuilder {
  private Map<String, String> fileHeader = Map.of();
  private final List<Map<String, String>> batchHeaders = new ArrayList<>();
  private final List<List<PendingEntry>> batchEntries = new ArrayList<>();

  private record PendingEntry(Map<String, String> detail, List<NachaRecord> addenda) {}

  public NachaBuilder header(Map<String, String> values) {
    this.fileHeader = values;
    return this;
  }

  /** Starts a batch; following {@link #entry} calls belong to it. */
  public NachaBuilder batch(Map<String, String> values) {
    batchHeaders.add(values);
    batchEntries.add(new ArrayList<>());
    return this;
  }

  public NachaBuilder entry(Map<String, String> values) {
    currentBatch().add(new PendingEntry(values, new ArrayList<>()));
    return this;
  }

  /** Adds an addenda record to the last entry. The type code is set from the layout. */
  public NachaBuilder addenda(Layout layout, Map<String, String> values) {
    List<PendingEntry> entries = currentBatch();
    if (entries.isEmpty()) {
      throw new NachaException("Add an entry before its addenda.");
    }
    Map<String, String> withCode = new LinkedHashMap<>(values);
    withCode.put(
        "typeCode",
        layout == Layouts.ADDENDA_98 ? "98" : layout == Layouts.ADDENDA_99 ? "99" : "05");
    entries.get(entries.size() - 1).addenda().add(NachaRecord.of(layout, '7', withCode));
    return this;
  }

  public NachaFile build() {
    Map<String, String> h = new LinkedHashMap<>();
    h.put("priorityCode", "01");
    h.put("recordSize", "094");
    h.put("blockingFactor", "10");
    h.put("formatCode", "1");
    h.putAll(fileHeader);

    List<NachaFile.Batch> batches = new ArrayList<>();
    long fileCount = 0;
    long fileHash = 0;
    long fileDebit = 0;
    long fileCredit = 0;
    long records = 2; // file header and file control

    for (int b = 0; b < batchHeaders.size(); b++) {
      NachaRecord header = NachaRecord.of(Layouts.BATCH_HEADER, '5', batchHeaders.get(b));
      List<NachaFile.Entry> entries = new ArrayList<>();
      long count = 0;
      long hash = 0;
      long debit = 0;
      long credit = 0;
      for (PendingEntry pending : batchEntries.get(b)) {
        Map<String, String> detail = new LinkedHashMap<>(pending.detail());
        detail.put("addendaIndicator", pending.addenda().isEmpty() ? "0" : "1");
        NachaRecord entry = NachaRecord.of(Layouts.ENTRY_DETAIL, '6', detail);
        String code = entry.get("transactionCode");
        if (!NachaAmounts.isKnown(code)) {
          throw new NachaException(
              "Transaction code '"
                  + code
                  + "' is not supported yet. Known: 22, 23, 27, 28, 32, 33, 37, 38.");
        }
        long amount = entry.number("amount");
        if (NachaAmounts.isCredit(code)) {
          credit += amount;
        } else {
          debit += amount;
        }
        hash = NachaAmounts.addHash(hash, entry.number("rdfiId"));
        count += 1 + pending.addenda().size();
        entries.add(new NachaFile.Entry(entry, pending.addenda()));
      }
      Map<String, String> c = new LinkedHashMap<>();
      c.put("serviceClassCode", header.get("serviceClassCode"));
      c.put("entryAddendaCount", Long.toString(count));
      c.put("entryHash", Long.toString(hash));
      c.put("totalDebit", Long.toString(debit));
      c.put("totalCredit", Long.toString(credit));
      c.put("companyId", header.get("companyId"));
      c.put("odfiId", header.get("odfiId"));
      c.put("batchNumber", header.get("batchNumber"));
      batches.add(
          new NachaFile.Batch(header, entries, NachaRecord.of(Layouts.BATCH_CONTROL, '8', c)));
      fileCount += count;
      fileHash = NachaAmounts.addHash(fileHash, hash);
      fileDebit += debit;
      fileCredit += credit;
      records += 2 + count;
    }

    long blocks = (records + 9) / 10;
    Map<String, String> fc = new LinkedHashMap<>();
    fc.put("batchCount", Integer.toString(batches.size()));
    fc.put("blockCount", Long.toString(blocks));
    fc.put("entryAddendaCount", Long.toString(fileCount));
    fc.put("entryHash", Long.toString(fileHash));
    fc.put("totalDebit", Long.toString(fileDebit));
    fc.put("totalCredit", Long.toString(fileCredit));
    return new NachaFile(
        NachaRecord.of(Layouts.FILE_HEADER, '1', h),
        batches,
        NachaRecord.of(Layouts.FILE_CONTROL, '9', fc),
        (int) (blocks * 10 - records));
  }

  private List<PendingEntry> currentBatch() {
    if (batchEntries.isEmpty()) {
      throw new NachaException("Start a batch before adding entries.");
    }
    return batchEntries.get(batchEntries.size() - 1);
  }
}
