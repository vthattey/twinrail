package io.github.vthattey.twinrail.testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.nacha.NachaBuilder;
import io.github.vthattey.twinrail.core.nacha.NachaCodec;
import io.github.vthattey.twinrail.core.rules.RulePackLoader;
import io.github.vthattey.twinrail.server.Emulator;
import io.github.vthattey.twinrail.server.TwinrailServer;
import io.github.vthattey.twinrail.testcontainers.TwinrailClient.AchState;
import io.github.vthattey.twinrail.testcontainers.TwinrailClient.PaymentState;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The client against the real server, in process. No Docker needed. */
class TwinrailClientTest {
  private static final Instant START = Instant.parse("2026-11-10T10:00:00Z");

  private TwinrailServer server;
  private TwinrailClient client;

  @BeforeEach
  void start() throws Exception {
    String pack;
    try (var in = getClass().getResourceAsStream("/packs/example.yaml")) {
      pack = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    var config =
        new Emulator.Config(
            1, START, Duration.ofSeconds(30), RulePackLoader.load(pack, "example.yaml"));
    server = new TwinrailServer(new Emulator(config), (t, h, b) -> 200).start(0);
    client = new TwinrailClient(URI.create("http://localhost:" + server.port() + "/"));
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  private static String pacs008(String replaceFrom, String replaceTo) throws Exception {
    try (var in = TwinrailClientTest.class.getResourceAsStream("/pacs008.xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace(replaceFrom, replaceTo);
    }
  }

  private static String achCredits() {
    var base =
        new NachaBuilder()
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
    base.entry(
        Map.of(
            "transactionCode", "22",
            "rdfiId", "11111111",
            "checkDigit", "8",
            "accountNumber", "ACC-1",
            "amount", "150000",
            "individualId", "ID",
            "individualName", "JANE DOE",
            "traceNumber", "123456780000001"));
    return NachaCodec.write(base.build());
  }

  @Test
  void aSettledPaymentComesBackTyped() throws Exception {
    var p = client.sendMessage(pacs008("E2E-1", "E2E-1"));
    assertEquals("PMT-000001", p.id());
    assertEquals(PaymentState.SETTLED, p.state());
    assertEquals(new BigDecimal("125.50"), p.amount());
    assertEquals("CAD", p.currency());
    assertEquals(START, p.receivedAt());
    assertNull(p.rejection());
    assertFalse(p.isRejected());
    assertEquals(p, client.payment("PMT-000001"));
    assertEquals(1, client.payments().size());
  }

  @Test
  void aRejectionIsAResultNotAnException() throws Exception {
    var p = client.sendMessage(pacs008("125.50<", "0.00<"));
    assertTrue(p.isRejected());
    assertEquals("EX-002", p.rejection().rule());
    assertEquals("AM01", p.rejection().reason());
    assertTrue(p.rejection().source().contains("Twinrail example pack"));
  }

  @Test
  void movingTheClockTimesOutADroppedPayment() throws Exception {
    assertEquals(
        1,
        client.setScenarios("format: 1\nscenarios:\n  - id: hole\n    action: { type: drop }\n"));
    var p = client.sendMessage(pacs008("E2E-1", "E2E-1"));
    assertEquals(PaymentState.ACCEPTED, p.state());

    var result = client.advance(Duration.ofSeconds(30));
    assertEquals(START.plusSeconds(30), result.now());
    assertEquals(1, result.transitions().size());
    assertEquals(PaymentState.TIMED_OUT, result.transitions().get(0).to());
    assertEquals(PaymentState.ACCEPTED, result.transitions().get(0).from());
    assertEquals(PaymentState.TIMED_OUT, client.payment(p.id()).state());
    assertEquals(START.plusSeconds(30), client.now());
    assertTrue(result.achTransitions().isEmpty());
  }

  @Test
  void setClockMovesToAnInstantButNeverBack() {
    client.setClock(START.plusSeconds(3600));
    assertEquals(START.plusSeconds(3600), client.now());
    var ex = assertThrows(TwinrailException.class, () -> client.setClock(START));
    assertEquals(400, ex.status());
    assertTrue(ex.getMessage().contains("forward"));
  }

  @Test
  void accountsAndStatements() throws Exception {
    client.openAccount("000000001", "CAD", new BigDecimal("500.00"));
    client.sendMessage(pacs008("E2E-1", "E2E-1"));

    var a = client.account("000000001");
    assertEquals(new BigDecimal("374.50"), a.balance());
    assertEquals(new BigDecimal("374.50"), a.available());
    assertTrue(client.statement("000000001").contains("camt.053.001.08"));
    assertTrue(client.accounts().stream().anyMatch(x -> x.id().equals("000000002")));
    assertTrue(client.findAccount("nobody").isEmpty());
    assertEquals("000000001", client.findAccount("000000001").orElseThrow().id());
  }

  @Test
  void achReturnAndChangeAreTyped() {
    client.setScenarios(
        "format: 1\nscenarios:\n  - id: closed\n    match: { message: nacha.entry }\n"
            + "    action: { type: return, code: R02, after: PT1H }\n");
    var result = client.sendAchFile(achCredits());
    assertFalse(result.rejected());
    assertEquals(AchState.SETTLED, result.entries().get(0).state());

    var due = client.advance(Duration.ofHours(1));
    assertEquals(1, due.achTransitions().size());
    assertEquals("returned", due.achTransitions().get(0).kind());

    var entry = client.achEntry(result.entries().get(0).id());
    assertEquals(AchState.RETURNED, entry.state());
    assertEquals("R02", entry.returned().code());
    assertEquals("Account Closed", entry.returned().title());
    assertEquals("2 Banking Days", entry.returned().timeframe());
    assertEquals(94, entry.returned().addendaRecord().length());
    assertEquals(1, client.achEntries().size());

    client.setScenarios(
        "format: 1\nscenarios:\n  - id: moved\n    action: { type: noc, code: C01, account: \"55555555\" }\n");
    var changed = client.sendAchFile(achCredits().replace("123456780000001", "123456780000009"));
    assertEquals("C01", changed.entries().get(0).change().code());
    assertEquals("55555555", changed.entries().get(0).change().corrected().get("account"));
  }

  @Test
  void anAchFileRefusedWholeListsItsProblems() {
    var result = client.sendAchFile(achCredits().replace("000000150000", "000000150001"));
    assertTrue(result.rejected());
    assertTrue(result.entries().isEmpty());
    assertTrue(result.problems().stream().anyMatch(p -> p.id().equals("nacha.batch.total-credit")));
    assertTrue(client.achEntries().isEmpty());
  }

  @Test
  void webhooksCanBeRegisteredListedAndRemoved() throws Exception {
    var target = client.registerWebhook("http://localhost:9/hook", "s3cret");
    assertEquals("WH-001", target.id());
    assertEquals(1, client.webhooks().size());

    client.sendMessage(pacs008("E2E-1", "E2E-1"));
    var deliveries = client.deliveries();
    assertEquals(2, deliveries.size(), "accepted and settled");
    assertEquals("DELIVERED", deliveries.get(0).state());
    assertNull(deliveries.get(0).nextAttemptAt());

    client.removeWebhook(target.id());
    assertTrue(client.webhooks().isEmpty());
    var ex =
        assertThrows(
            TwinrailException.class, () -> client.registerWebhook("http://example.com/x", "s"));
    assertEquals(400, ex.status());
    assertTrue(ex.getMessage().contains("not local"));
  }

  @Test
  void refusalsCarryTheEmulatorsExplanation() {
    var notXml = assertThrows(TwinrailException.class, () -> client.sendMessage("hello"));
    assertEquals(400, notXml.status());
    assertTrue(notXml.getMessage().contains("Check"));

    var missing = assertThrows(TwinrailException.class, () -> client.payment("PMT-999999"));
    assertEquals(404, missing.status());
    assertTrue(missing.getMessage().contains("GET /_twinrail/payments"));

    var badScenario =
        assertThrows(
            TwinrailException.class,
            () ->
                client.setScenarios(
                    "format: 1\nscenarios:\n  - id: x\n    action: { type: nope }\n"));
    assertEquals(400, badScenario.status());
  }

  @Test
  void resetReturnsToTheStartState() throws Exception {
    client.sendMessage(pacs008("E2E-1", "E2E-1"));
    client.advance(Duration.ofMinutes(5));
    client.reset();
    assertTrue(client.payments().isEmpty());
    assertEquals(START, client.now());
  }

  @Test
  void healthAndSessionAndUnreachableServer() throws Exception {
    assertTrue(client.isHealthy());
    client.sendMessage(pacs008("E2E-1", "E2E-1"));
    assertTrue(client.session().contains("pacs.008.001.08"));

    var nowhere = new TwinrailClient(URI.create("http://localhost:1"));
    assertFalse(nowhere.isHealthy());
    var ex = assertThrows(TwinrailException.class, nowhere::now);
    assertEquals(-1, ex.status());
    assertTrue(ex.getMessage().contains("Is the container started?"));
    assertNotNull(ex.getCause());
  }
}
