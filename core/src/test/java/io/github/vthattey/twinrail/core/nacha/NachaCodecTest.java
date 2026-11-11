package io.github.vthattey.twinrail.core.nacha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.TestMessages;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NachaCodecTest {
  private static NachaBuilder sampleBuilder() {
    return new NachaBuilder()
        .header(
            Map.of(
                "immediateDestination", " 987654320",
                "immediateOrigin", " 123456780",
                "fileCreationDate", "261110",
                "fileCreationTime", "1400",
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
                "batchNumber", "1"))
        .entry(entry("22", "11111111", "8", "123456789", "150000", "JANE DOE", "123456780000001"))
        .entry(entry("22", "22222222", "6", "987654321", "212550", "JOHN ROE", "123456780000002"));
  }

  private static Map<String, String> entry(
      String code,
      String rdfi,
      String check,
      String account,
      String cents,
      String name,
      String trace) {
    return Map.of(
        "transactionCode", code,
        "rdfiId", rdfi,
        "checkDigit", check,
        "accountNumber", account,
        "amount", cents,
        "individualId", "EMP",
        "individualName", name,
        "traceNumber", trace);
  }

  private static List<String> lines(String text) {
    return List.of(text.split("\n"));
  }

  @Test
  void builtFileHasTheDerivedControlValues() {
    NachaFile file = sampleBuilder().build();
    String text = NachaCodec.write(file);
    List<String> lines = lines(text);

    assertEquals(10, lines.size(), "6 records padded to one block of 10 lines");
    assertTrue(lines.stream().allMatch(l -> l.length() == 94));
    assertEquals("9".repeat(94), lines.get(9));

    // Expected values worked out by hand from the rules in docs/research.md section 7:
    // hash 11111111 + 22222222 = 33333333, credits 150000 + 212550 = 362550 cents, 2 entries, 1
    // block.
    NachaRecord batchControl = file.batches().get(0).control();
    assertEquals(2, batchControl.number("entryAddendaCount"));
    assertEquals(33333333, batchControl.number("entryHash"));
    assertEquals(0, batchControl.number("totalDebit"));
    assertEquals(362550, batchControl.number("totalCredit"));
    assertEquals("0033333333", lines.get(4).substring(10, 20));
    assertEquals("000000362550", lines.get(4).substring(32, 44));
    assertEquals(1, file.control().number("blockCount"));
    assertEquals(2, file.control().number("entryAddendaCount"));
  }

  @Test
  void writtenFileMatchesTheGoldenFile() {
    assertEquals(
        TestMessages.resource("/nacha/credits-golden.ach"),
        NachaCodec.write(sampleBuilder().build()));
  }

  @Test
  void parseThenWriteGivesTheSameBytes() {
    String text = NachaCodec.write(sampleBuilder().build());
    NachaFile parsed = NachaCodec.parse(text);
    assertEquals(text, NachaCodec.write(parsed));
    assertEquals(6, parsed.records().size());
    assertEquals(4, parsed.paddingLines());
    assertEquals(
        "JANE DOE", parsed.batches().get(0).entries().get(0).detail().get("individualName"));
    assertEquals(1, parsed.batches().get(0).entries().get(0).detail().line() - 2);
  }

  @Test
  void aConsistentFileHasNoProblems() {
    assertEquals(List.of(), NachaCheck.verify(sampleBuilder().build()));
    assertEquals(
        List.of(), NachaCheck.verify(NachaCodec.parse(NachaCodec.write(sampleBuilder().build()))));
  }

  @Test
  void windowsLineEndingsAndNoTrailingNewlineAreAccepted() {
    String text = NachaCodec.write(sampleBuilder().build());
    NachaFile crlf = NachaCodec.parse(text.replace("\n", "\r\n"));
    NachaFile bare = NachaCodec.parse(text.stripTrailing());
    assertEquals(6, crlf.records().size());
    assertEquals(NachaCodec.write(crlf), NachaCodec.write(bare));
  }

  @Test
  void wrongControlValuesAreReportedWithStableIds() {
    String text = NachaCodec.write(sampleBuilder().build());
    List<String> lines = new java.util.ArrayList<>(lines(text));
    // Batch control (line 5): credit total 362550 -> 362551, hash 33333333 -> 33333334.
    lines.set(
        4,
        lines.get(4).replace("000000362550", "000000362551").replace("0033333333", "0033333334"));
    // File control (line 6): entry count 2 -> 3.
    lines.set(5, lines.get(5).replace("00000002", "00000003"));
    var problems = NachaCheck.verify(NachaCodec.parse(String.join("\n", lines) + "\n"));
    var ids = problems.stream().map(NachaCheck.Problem::id).toList();

    assertTrue(ids.contains("nacha.batch.total-credit"), ids.toString());
    assertTrue(ids.contains("nacha.batch.hash"), ids.toString());
    assertTrue(ids.contains("nacha.file.count"), ids.toString());
    var credit =
        problems.stream()
            .filter(p -> p.id().equals("nacha.batch.total-credit"))
            .findFirst()
            .orElseThrow();
    assertEquals(5, credit.line());
    assertTrue(credit.text().contains("362551") && credit.text().contains("362550"));
  }

  @Test
  void missingPaddingIsReported() {
    String text = NachaCodec.write(sampleBuilder().build());
    String unpadded = String.join("\n", lines(text).subList(0, 6)) + "\n";
    var ids =
        NachaCheck.verify(NachaCodec.parse(unpadded)).stream().map(NachaCheck.Problem::id).toList();
    assertTrue(ids.contains("nacha.file.padding"), ids.toString());
  }

  @Test
  void returnAndChangeAddendaRoundTrip() {
    NachaFile file =
        sampleBuilder()
            .addenda(
                Layouts.ADDENDA_99,
                Map.of(
                    "returnCode", "R01",
                    "originalTrace", "123456780000002",
                    "originalRdfiId", "22222222",
                    "traceNumber", "222222220000001"))
            .addenda(
                Layouts.ADDENDA_98,
                Map.of(
                    "changeCode", "C01",
                    "originalTrace", "123456780000002",
                    "originalRdfiId", "22222222",
                    "correctedData", "55555555",
                    "traceNumber", "222222220000002"))
            .build();
    String text = NachaCodec.write(file);
    NachaFile parsed = NachaCodec.parse(text);

    var entry = parsed.batches().get(0).entries().get(1);
    assertEquals("1", entry.detail().get("addendaIndicator"));
    assertEquals(2, entry.addenda().size());
    assertEquals("R01", entry.addenda().get(0).get("returnCode"));
    assertEquals("123456780000002", entry.addenda().get(0).get("originalTrace"));
    assertEquals("C01", entry.addenda().get(1).get("changeCode"));
    assertEquals("55555555", entry.addenda().get(1).get("correctedData"));
    assertEquals(4, parsed.batches().get(0).control().number("entryAddendaCount"));
    assertEquals(List.of(), NachaCheck.verify(parsed));
    assertEquals(text, NachaCodec.write(parsed));
  }

  @Test
  void structuralErrorsNameTheLineAndWhatToDo() {
    String good = NachaCodec.write(sampleBuilder().build());
    List<String> lines = lines(good);

    var shortLine = new java.util.ArrayList<>(lines);
    shortLine.set(2, shortLine.get(2).substring(0, 90));
    assertTrue(message(String.join("\n", shortLine)).contains("Line 3 is 90 characters"));

    var orphanAddenda = new java.util.ArrayList<>(lines);
    orphanAddenda.add(1, "705" + " ".repeat(91));
    assertTrue(message(String.join("\n", orphanAddenda)).contains("no entry detail"));

    var badAddenda = new java.util.ArrayList<>(lines);
    badAddenda.add(3, "777" + " ".repeat(91));
    assertTrue(message(String.join("\n", badAddenda)).contains("addenda type code '77'"));

    var junk = new java.util.ArrayList<>(lines);
    junk.add("1".repeat(94));
    assertTrue(message(String.join("\n", junk)).contains("after the file control record"));

    assertTrue(
        message(String.join("\n", lines.subList(0, 5))).contains("no control record")
            || message(String.join("\n", lines.subList(0, 5))).contains("no file control"));
    assertTrue(message("Z".repeat(94)).contains("not a record type"));
    assertTrue(message("  ").contains("empty"));
  }

  @Test
  void builderRefusesWhatItCannotDescribeCorrectly() {
    var tooLong =
        assertThrows(
            NachaException.class,
            () ->
                new NachaBuilder()
                    .header(Map.of("destinationName", "X".repeat(40)))
                    .build()
                    .records()
                    .forEach(NachaRecord::toLine));
    assertTrue(tooLong.getMessage().contains("destinationName"));

    var unknownCode =
        assertThrows(
            NachaException.class,
            () ->
                sampleBuilder()
                    .entry(entry("55", "11111111", "8", "1", "100", "X", "123456780000003"))
                    .build());
    assertTrue(unknownCode.getMessage().contains("not supported"));
  }

  private static String message(String text) {
    return assertThrows(NachaException.class, () -> NachaCodec.parse(text)).getMessage();
  }
}
