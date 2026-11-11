package io.github.vthattey.twinrail.core.nacha;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes ACH files. Parsing checks structure only (94-character lines, record order,
 * known record and addenda types); control totals are checked separately by {@link NachaCheck}, so
 * a file with wrong totals can still be read and reported on.
 *
 * <p>Output is deterministic: LF line endings, a trailing newline, padding with lines of nines to a
 * multiple of ten lines.
 */
public final class NachaCodec {
  private static final String NINES = "9".repeat(Layout.RECORD_LENGTH);

  private NachaCodec() {}

  /**
   * @throws NachaException if the text is not structurally an ACH file; the message gives the line
   *     number and what to fix
   */
  public static NachaFile parse(String text) {
    if (text == null || text.isBlank()) {
      throw new NachaException("The file is empty. Send an ACH file: 94-character records.");
    }
    List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
    if (lines.get(lines.size() - 1).isEmpty()) {
      lines.remove(lines.size() - 1); // one trailing newline is normal
    }

    NachaRecord fileHeader = null;
    NachaRecord fileControl = null;
    List<NachaFile.Batch> batches = new ArrayList<>();
    NachaRecord batchHeader = null;
    List<NachaFile.Entry> entries = new ArrayList<>();
    NachaRecord entry = null;
    List<NachaRecord> addenda = new ArrayList<>();
    int padding = 0;

    for (int i = 0; i < lines.size(); i++) {
      int n = i + 1;
      String line = lines.get(i);
      if (line.length() != Layout.RECORD_LENGTH) {
        throw new NachaException(
            "Line "
                + n
                + " is "
                + line.length()
                + " characters; every record must be exactly "
                + Layout.RECORD_LENGTH
                + ". Check for trimmed trailing spaces or a stray blank line.");
      }
      if (fileControl != null) {
        if (!line.equals(NINES)) {
          throw new NachaException(
              "Line "
                  + n
                  + " comes after the file control record but is not a padding line of 94 nines. "
                  + "Nothing may follow the file control record except padding.");
        }
        padding++;
        continue;
      }
      char type = line.charAt(0);
      switch (type) {
        case '1' -> {
          if (fileHeader != null || n != 1) {
            throw new NachaException(
                "Line " + n + " is a file header (type 1) but only line 1 may be.");
          }
          fileHeader = record(Layouts.FILE_HEADER, line, n);
        }
        case '5' -> {
          expectOpen(fileHeader, n, "a batch header (type 5)");
          if (batchHeader != null) {
            throw new NachaException(
                "Line "
                    + n
                    + " starts a batch before the previous batch's control record (type 8).");
          }
          batchHeader = record(Layouts.BATCH_HEADER, line, n);
        }
        case '6' -> {
          expectBatch(batchHeader, n, "an entry detail (type 6)");
          if (entry != null) {
            entries.add(new NachaFile.Entry(entry, addenda));
            addenda = new ArrayList<>();
          }
          entry = record(Layouts.ENTRY_DETAIL, line, n);
        }
        case '7' -> {
          if (entry == null) {
            throw new NachaException(
                "Line "
                    + n
                    + " is an addenda record (type 7) but no entry detail (type 6) precedes it.");
          }
          String code = line.substring(1, 3);
          Layout layout = Layouts.addendaFor(code);
          if (layout == null) {
            throw new NachaException(
                "Line "
                    + n
                    + " has addenda type code '"
                    + code
                    + "'. Supported: 05 (payment information), 98 (notification of change), 99 (return).");
          }
          addenda.add(record(layout, line, n));
        }
        case '8' -> {
          expectBatch(batchHeader, n, "a batch control (type 8)");
          if (entry != null) {
            entries.add(new NachaFile.Entry(entry, addenda));
            entry = null;
            addenda = new ArrayList<>();
          }
          batches.add(
              new NachaFile.Batch(batchHeader, entries, record(Layouts.BATCH_CONTROL, line, n)));
          batchHeader = null;
          entries = new ArrayList<>();
        }
        case '9' -> {
          expectOpen(fileHeader, n, "a file control (type 9)");
          if (batchHeader != null) {
            throw new NachaException(
                "Line "
                    + n
                    + " ends the file but the batch that started before it has no control record (type 8).");
          }
          fileControl = record(Layouts.FILE_CONTROL, line, n);
        }
        default ->
            throw new NachaException(
                "Line "
                    + n
                    + " starts with '"
                    + type
                    + "', which is not a record type. Types are 1, 5, 6, 7, 8 and 9.");
      }
    }
    if (fileHeader == null) {
      throw new NachaException("The file has no file header (type 1) on line 1.");
    }
    if (batchHeader != null) {
      throw new NachaException("The last batch has no control record (type 8).");
    }
    if (fileControl == null) {
      throw new NachaException("The file has no file control record (type 9).");
    }
    return new NachaFile(fileHeader, batches, fileControl, padding);
  }

  /** Writes the file, padding the last block with lines of nines. */
  public static String write(NachaFile file) {
    List<String> lines = new ArrayList<>();
    for (NachaRecord r : file.records()) {
      lines.add(r.toLine());
    }
    while (lines.size() % 10 != 0) {
      lines.add(NINES);
    }
    return String.join("\n", lines) + "\n";
  }

  private static NachaRecord record(Layout layout, String line, int n) {
    return new NachaRecord(layout, line.charAt(0), layout.slice(line), n);
  }

  private static void expectOpen(NachaRecord fileHeader, int n, String what) {
    if (fileHeader == null) {
      throw new NachaException(
          "Line " + n + " is " + what + " but the file has no file header yet.");
    }
  }

  private static void expectBatch(NachaRecord batchHeader, int n, String what) {
    if (batchHeader == null) {
      throw new NachaException(
          "Line "
              + n
              + " is "
              + what
              + " outside a batch. A batch header (type 5) must come first.");
    }
  }
}
