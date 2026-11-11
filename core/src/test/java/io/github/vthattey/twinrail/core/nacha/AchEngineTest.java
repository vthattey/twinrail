package io.github.vthattey.twinrail.core.nacha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.clock.VirtualClock;
import io.github.vthattey.twinrail.core.definition.DefinitionException;
import io.github.vthattey.twinrail.core.ids.SeededIds;
import io.github.vthattey.twinrail.core.ledger.Ledger;
import io.github.vthattey.twinrail.core.nacha.AchModel.State;
import io.github.vthattey.twinrail.core.scenario.ScenarioLoader;
import io.github.vthattey.twinrail.core.scenario.ScenarioSet;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AchEngineTest {
  private static final Instant START = Instant.parse("2026-11-10T10:00:00Z");
  private static final String SETTLEMENT = "ACH-SETTLEMENT";

  private VirtualClock clock;
  private Ledger ledger;
  private ReasonCodes codes;

  @BeforeEach
  void setUp() throws IOException {
    clock = new VirtualClock(START);
    ledger = new Ledger();
    codes = ReasonCodesTest.shipped();
  }

  private AchEngine engine(String scenarioYaml) {
    ScenarioSet set =
        scenarioYaml == null ? ScenarioSet.empty() : ScenarioLoader.load(scenarioYaml, "t.yaml");
    return new AchEngine(AchEngine.Config.defaults(), clock, new SeededIds(1), set, ledger, codes);
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
        "individualId", "ID-" + trace.substring(trace.length() - 2),
        "individualName", name,
        "traceNumber", trace);
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
                "serviceClassCode", "200",
                "companyName", "ACME CORP",
                "companyId", "1123456789",
                "secCode", "PPD",
                "entryDescription", "PAYROLL",
                "effectiveEntryDate", "261111",
                "originatorStatusCode", "1",
                "odfiId", "12345678",
                "batchNumber", "1"));
  }

  /** Two credits: 1500.00 to ACC-1 and 2125.50 to ACC-2. */
  private static String creditFile() {
    return NachaCodec.write(
        base()
            .entry(entry("22", "11111111", "8", "ACC-1", "150000", "JANE DOE", "123456780000001"))
            .entry(entry("22", "22222222", "6", "ACC-2", "212550", "JOHN ROE", "123456780000002"))
            .build());
  }

  private static String debitFile(String account, String cents) {
    return NachaCodec.write(
        base()
            .entry(entry("27", "11111111", "8", account, cents, "JANE DOE", "123456780000001"))
            .build());
  }

  @Test
  void creditsSettleAndMoveMoneyFromTheSettlementAccount() {
    var engine = engine(null);
    var result = engine.submit(creditFile());

    assertTrue(!result.rejected());
    assertEquals(2, result.entries().size());
    assertEquals(State.SETTLED, result.entries().get(0).state());
    assertEquals("ACH-000001", result.entries().get(0).id());
    assertEquals(new BigDecimal("1500.00"), ledger.balance("ACC-1"));
    assertEquals(new BigDecimal("2125.50"), ledger.balance("ACC-2"));
    assertEquals(new BigDecimal("-3625.50"), ledger.balance(SETTLEMENT));
    assertEquals(0, ledger.total().signum(), "money is conserved");
  }

  @Test
  void aDebitTheAccountCannotCoverIsReturnedR01WithoutBooking() {
    ledger.open("ACC-1", "USD", new BigDecimal("100.00"));
    var entry = engine(null).submit(debitFile("ACC-1", "150000")).entries().get(0);

    assertEquals(State.RETURNED, entry.state());
    assertEquals("R01", entry.returned().code());
    assertEquals("Insufficient Funds", entry.returned().title());
    assertEquals("2 Banking Days", entry.returned().timeframe());
    assertTrue(entry.returned().source().contains("Modern Treasury"));
    assertTrue(ledger.entries().isEmpty());
    assertEquals(new BigDecimal("100.00"), ledger.balance("ACC-1"));
  }

  @Test
  void aCoveredDebitSettlesAndPullsMoneyFromTheReceiver() {
    ledger.open("ACC-1", "USD", new BigDecimal("2000.00"));
    var entry = engine(null).submit(debitFile("ACC-1", "150000")).entries().get(0);
    assertEquals(State.SETTLED, entry.state());
    assertEquals(new BigDecimal("500.00"), ledger.balance("ACC-1"));
    assertEquals(new BigDecimal("1500.00"), ledger.balance(SETTLEMENT));
  }

  @Test
  void aReturnScenarioReturnsAfterTheConfiguredTimeAndReversesTheBooking() {
    var engine =
        engine(
            """
            format: 1
            scenarios:
              - id: closed
                match:
                  message: nacha.entry
                  where: [{ path: /Entry/IndividualName, equals: "JANE DOE" }]
                action: { type: return, code: R02, after: PT1H, text: Account closed }
            """);
    String id = engine.submit(creditFile()).entries().get(0).id();
    assertEquals(State.SETTLED, engine.find(id).orElseThrow().state());
    assertEquals(new BigDecimal("1500.00"), ledger.balance("ACC-1"));

    clock.advance(Duration.ofMinutes(59));
    assertTrue(engine.tick().isEmpty());

    clock.advance(Duration.ofMinutes(1));
    assertEquals(1, engine.tick().size());
    var returned = engine.find(id).orElseThrow();
    assertEquals(State.RETURNED, returned.state());
    assertEquals(START.plus(Duration.ofHours(1)), returned.returned().at());
    assertEquals("R02", returned.returned().code());
    assertEquals(0, ledger.balance("ACC-1").signum(), "the credit was taken back");
    assertEquals(
        new BigDecimal("2125.50"), ledger.balance("ACC-2"), "the other entry is untouched");

    String line = returned.returned().addendaRecord();
    assertEquals(94, line.length());
    assertEquals(
        "799R02123456780000001", line.substring(0, 21), "type 7, code 99, R02, original trace");
    assertEquals("11111111", line.substring(27, 35));
    assertTrue(line.substring(35, 79).startsWith("Account closed"));
  }

  @Test
  void aReturnWithNoDelayHappensAtOnce() {
    var engine =
        engine("format: 1\nscenarios:\n  - id: r\n    action: { type: return, code: R03 }\n");
    var entry = engine.submit(creditFile()).entries().get(0);
    assertEquals(State.RETURNED, entry.state());
    assertEquals(0, ledger.balance("ACC-1").signum());
  }

  @Test
  void aNocScenarioAttachesAChangeButKeepsTheEntrySettled() {
    var engine =
        engine(
            """
            format: 1
            scenarios:
              - id: moved
                match: { message: nacha.entry, where: [{ path: /Entry/AccountNumber, equals: ACC-2 }] }
                action: { type: noc, code: C01, account: "55555555" }
            """);
    var entries = engine.submit(creditFile()).entries();

    assertNull(entries.get(0).change());
    var changed = entries.get(1);
    assertEquals(State.SETTLED, changed.state());
    assertEquals("C01", changed.change().code());
    assertEquals("Incorrect DFI Account Number", changed.change().title());
    String line = changed.change().addendaRecord();
    assertEquals(94, line.length());
    assertEquals("798C01123456780000002", line.substring(0, 21));
    assertEquals("55555555", line.substring(35, 43));
    assertEquals("22222222", line.substring(27, 35));
  }

  @Test
  void c03PutsTheRoutingNumberFirstAndTheAccountLast() {
    var engine =
        engine(
            "format: 1\nscenarios:\n  - id: m\n    action: { type: noc, code: C03, routing: \"021000021\","
                + " account: \"12345\" }\n");
    String line = engine.submit(creditFile()).entries().get(0).change().addendaRecord();
    String field = line.substring(35, 64);
    assertEquals(29, field.length());
    assertTrue(field.startsWith("021000021"));
    assertTrue(field.endsWith("12345"));
  }

  @Test
  void aDelayScenarioSettlesLater() {
    var engine =
        engine(
            "format: 1\nscenarios:\n  - id: slow\n    action: { type: delay, duration: PT10M }\n");
    String id = engine.submit(creditFile()).entries().get(0).id();
    assertEquals(State.ACCEPTED, engine.find(id).orElseThrow().state());
    assertTrue(ledger.entries().isEmpty());
    clock.advance(Duration.ofMinutes(10));
    engine.tick();
    assertEquals(State.SETTLED, engine.find(id).orElseThrow().state());
    assertEquals(START.plus(Duration.ofMinutes(10)), ledger.entries().get(0).bookedAt());
  }

  @Test
  void rejectAndDropDoNotApplyToAchEntries() {
    var engine =
        engine(
            "format: 1\nscenarios:\n  - id: r\n    match: { message: nacha.entry }\n"
                + "    action: { type: reject, reason: AC04 }\n");
    assertEquals(State.SETTLED, engine.submit(creditFile()).entries().get(0).state());
  }

  @Test
  void aFileWithWrongControlTotalsIsRefusedWholeWithStableIds() {
    String file = creditFile();
    String broken = file.replace("000000362550", "000000362551");
    var engine = engine(null);
    var result = engine.submit(broken);

    assertTrue(result.rejected());
    assertTrue(result.entries().isEmpty());
    assertTrue(result.problems().stream().anyMatch(p -> p.id().equals("nacha.batch.total-credit")));
    assertTrue(ledger.entries().isEmpty());
    assertTrue(engine.entries().isEmpty());
  }

  @Test
  void scenariosWithUnknownCodesAreRefusedBeforeUse() {
    var engine = engine(null);
    var unknownReturn =
        assertThrows(
            DefinitionException.class,
            () ->
                engine.setScenarios(
                    ScenarioLoader.load(
                        "format: 1\nscenarios:\n  - id: x\n    action: { type: return, code: R99 }\n",
                        "t.yaml")));
    assertTrue(
        unknownReturn.getMessage().contains("R99") && unknownReturn.getMessage().contains("R01"));

    var unknownChange =
        assertThrows(
            DefinitionException.class,
            () ->
                engine.setScenarios(
                    ScenarioLoader.load(
                        "format: 1\nscenarios:\n  - id: x\n    action: { type: noc, code: C14, account: \"1\" }\n",
                        "t.yaml")));
    assertTrue(
        unknownChange.getMessage().contains("not supported")
            || unknownChange.getMessage().contains("C14"));

    var tooLong =
        assertThrows(
            DefinitionException.class,
            () ->
                engine.setScenarios(
                    ScenarioLoader.load(
                        "format: 1\nscenarios:\n  - id: x\n    action: { type: noc, code: C01, account: \""
                            + "9".repeat(40)
                            + "\" }\n",
                        "t.yaml")));
    assertTrue(tooLong.getMessage().contains("Shorten"));
  }

  @Test
  void sameInputsGiveTheSameEntriesAndLedger() {
    String yaml =
        "format: 1\nscenarios:\n  - id: r\n    action: { type: return, code: R02, after: PT5M }\n";
    List<String> runs =
        List.of(0, 1).stream()
            .map(i -> {
              var c = new VirtualClock(START);
              var l = new Ledger();
              var e =
                  new AchEngine(
                      AchEngine.Config.defaults(),
                      c,
                      new SeededIds(5),
                      ScenarioLoader.load(yaml, "t.yaml"),
                      l,
                      codes);
              e.submit(creditFile());
              c.advance(Duration.ofMinutes(10));
              e.tick();
              return e.entries() + "|" + l.entries() + "|" + e.history();
            })
            .toList();
    assertEquals(runs.get(0), runs.get(1));
  }
}
