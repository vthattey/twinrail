package io.github.vthattey.twinrail.core.nacha;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed or built ACH file: a file header, batches, a file control record, and (when read from
 * text) how many all-nines padding lines followed.
 */
public record NachaFile(
    NachaRecord header, List<Batch> batches, NachaRecord control, int paddingLines) {
  public NachaFile {
    batches = List.copyOf(batches);
  }

  /** A batch: header, entries (each with its addenda) and control. */
  public record Batch(NachaRecord header, List<Entry> entries, NachaRecord control) {
    public Batch {
      entries = List.copyOf(entries);
    }
  }

  /** An entry detail record and the addenda records that follow it. */
  public record Entry(NachaRecord detail, List<NachaRecord> addenda) {
    public Entry {
      addenda = List.copyOf(addenda);
    }
  }

  /** Every record in file order, without padding. */
  public List<NachaRecord> records() {
    List<NachaRecord> all = new ArrayList<>();
    all.add(header);
    for (Batch b : batches) {
      all.add(b.header());
      for (Entry e : b.entries()) {
        all.add(e.detail());
        all.addAll(e.addenda());
      }
      all.add(b.control());
    }
    all.add(control);
    return all;
  }
}
