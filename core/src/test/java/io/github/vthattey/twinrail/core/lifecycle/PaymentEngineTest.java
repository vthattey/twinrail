package io.github.vthattey.twinrail.core.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.TestMessages;
import io.github.vthattey.twinrail.core.clock.VirtualClock;
import io.github.vthattey.twinrail.core.ids.SeededIds;
import io.github.vthattey.twinrail.core.ledger.Ledger;
import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import io.github.vthattey.twinrail.core.rules.RulePack;
import io.github.vthattey.twinrail.core.rules.RulePackLoader;
import io.github.vthattey.twinrail.core.scenario.ScenarioLoader;
import io.github.vthattey.twinrail.core.scenario.ScenarioSet;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentEngineTest {
  private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");
  private static final String DEBTOR = "000000001";
  private static final String CREDITOR = "000000002";
  private static final String SLOW =
      """
      format: 1
      scenarios:
        - id: slow
          action: { type: delay, duration: PT10S }
      """;
  private static final String HOLE =
      """
      format: 1
      scenarios:
        - id: hole
          action: { type: drop }
      """;

  private VirtualClock clock;
  private Ledger ledger;
  private RulePack pack;

  @BeforeEach
  void setUp() {
    clock = new VirtualClock(START);
    ledger = new Ledger();
    pack = RulePackLoader.loadFile(Path.of(System.getProperty("rulePacksDir"), "example.yaml"));
  }

  private PaymentEngine engine(String scenariosYaml) {
    ScenarioSet set =
        scenariosYaml == null ? ScenarioSet.empty() : ScenarioLoader.load(scenariosYaml, "test");
    return new PaymentEngine(Duration.ofSeconds(30), clock, new SeededIds(1), pack, set, ledger);
  }

  private static XmlMessage credit(String amount) {
    return TestMessages.pacs008With("125.50<", amount + "<");
  }

  @Test
  void validPaymentSettlesImmediatelyAndMovesMoney() {
    ledger.open(DEBTOR, "CAD", new BigDecimal("500.00"));
    Payment p = engine(null).submit(TestMessages.pacs008());

    assertEquals(PaymentState.SETTLED, p.state());
    assertEquals("PMT-000001", p.id());
    assertEquals(new BigDecimal("374.50"), ledger.balance(DEBTOR));
    assertEquals(new BigDecimal("125.50"), ledger.balance(CREDITOR));
    assertEquals(2, ledger.entries().size());
  }

  @Test
  void ruleViolationRejectsWithRuleIdReasonAndSource() {
    Payment p = engine(null).submit(credit("0.00"));

    assertEquals(PaymentState.REJECTED, p.state());
    assertEquals("EX-002", p.rejection().ruleId());
    assertEquals("AM01", p.rejection().reason());
    assertTrue(p.rejection().source().contains("Twinrail example pack"));
    assertTrue(ledger.entries().isEmpty());
  }

  @Test
  void scenarioRejectCarriesScenarioIdAndReason() {
    String yaml =
        """
        format: 1
        scenarios:
          - id: closed
            action: { type: reject, reason: AC04, text: Account closed }
        """;
    Payment p = engine(yaml).submit(TestMessages.pacs008());

    assertEquals(PaymentState.REJECTED, p.state());
    assertEquals("scenario:closed", p.rejection().ruleId());
    assertEquals("AC04", p.rejection().reason());
  }

  @Test
  void delayedPaymentStaysAcceptedUntilClockPassesTheDelay() {
    PaymentEngine engine = engine(SLOW);
    Payment p = engine.submit(TestMessages.pacs008());
    assertEquals(PaymentState.ACCEPTED, p.state());

    clock.advance(Duration.ofSeconds(9));
    assertTrue(engine.tick().isEmpty());

    clock.advance(Duration.ofSeconds(1));
    assertEquals(1, engine.tick().size());
    assertEquals(PaymentState.SETTLED, engine.find(p.id()).orElseThrow().state());
    assertEquals(START.plusSeconds(10), engine.find(p.id()).orElseThrow().closedAt());
  }

  @Test
  void droppedPaymentTimesOutOnTheVirtualClockAndReleasesFunds() {
    ledger.open(DEBTOR, "CAD", new BigDecimal("200.00"));
    PaymentEngine engine = engine(HOLE);
    Payment p = engine.submit(TestMessages.pacs008());
    assertEquals(PaymentState.ACCEPTED, p.state());
    assertEquals(new BigDecimal("74.50"), ledger.available(DEBTOR));

    clock.advance(Duration.ofSeconds(29));
    engine.tick();
    assertEquals(PaymentState.ACCEPTED, engine.find(p.id()).orElseThrow().state());

    clock.advance(Duration.ofSeconds(1));
    engine.tick();
    Payment done = engine.find(p.id()).orElseThrow();
    assertEquals(PaymentState.TIMED_OUT, done.state());
    assertEquals(START.plusSeconds(30), done.closedAt());
    assertEquals(new BigDecimal("200.00"), ledger.available(DEBTOR));
    assertTrue(ledger.entries().isEmpty());
  }

  @Test
  void delayLongerThanTimeoutTimesOut() {
    String yaml =
        """
        format: 1
        scenarios:
          - id: too-slow
            action: { type: delay, duration: PT45S }
        """;
    PaymentEngine engine = engine(yaml);
    Payment p = engine.submit(TestMessages.pacs008());
    clock.advance(Duration.ofMinutes(5));
    engine.tick();
    assertEquals(PaymentState.TIMED_OUT, engine.find(p.id()).orElseThrow().state());
  }

  @Test
  void insufficientFundsRejectsWithAm04() {
    ledger.open(DEBTOR, "CAD", new BigDecimal("100.00"));
    Payment p = engine(null).submit(TestMessages.pacs008());

    assertEquals(PaymentState.REJECTED, p.state());
    assertEquals("twinrail.ledger.funds", p.rejection().ruleId());
    assertEquals("AM04", p.rejection().reason());
    assertEquals(new BigDecimal("100.00"), ledger.balance(DEBTOR));
  }

  @Test
  void pendingPaymentsReserveFundsSoTheSecondOneIsRejected() {
    ledger.open(DEBTOR, "CAD", new BigDecimal("200.00"));
    PaymentEngine engine = engine(SLOW);
    assertEquals(PaymentState.ACCEPTED, engine.submit(TestMessages.pacs008()).state());
    Payment second = engine.submit(TestMessages.pacs008());
    assertEquals(PaymentState.REJECTED, second.state());
    assertEquals("AM04", second.rejection().reason());
  }

  @Test
  void currencyDifferentFromAccountIsRejected() {
    ledger.open(DEBTOR, "USD", new BigDecimal("500.00"));
    Payment p = engine(null).submit(TestMessages.pacs008());
    assertEquals("AM03", p.rejection().reason());
  }

  @Test
  void jumpingTheClockBooksAtTheDueTimeNotTheTickTime() {
    PaymentEngine engine = engine(SLOW);
    engine.submit(TestMessages.pacs008());
    clock.advance(Duration.ofHours(1));
    engine.tick();
    assertEquals(START.plusSeconds(10), ledger.entries().get(0).bookedAt());
    assertNull(engine.payments().get(0).rejection());
  }

  @Test
  void nonCreditTransferIsRefusedWithAHelpfulMessage() {
    XmlMessage other = XmlMessage.parse("pacs.002.001.10", "<Document/>");
    var ex = assertThrows(IllegalArgumentException.class, () -> engine(null).submit(other));
    assertTrue(ex.getMessage().contains("pacs.008"));
  }

  @Test
  void historyListsEveryTransition() {
    PaymentEngine engine = engine(null);
    engine.submit(TestMessages.pacs008());
    assertEquals(2, engine.history().size());
    assertNull(engine.history().get(0).from());
    assertNotNull(engine.history().get(1).from());
  }

  @Test
  void sameInputsGiveIdenticalPaymentsAndLedger() {
    assertEquals(runOnce(), runOnce());
  }

  private String runOnce() {
    var c = new VirtualClock(START);
    var l = new Ledger();
    var e =
        new PaymentEngine(
            Duration.ofSeconds(30), c, new SeededIds(7), pack, ScenarioSet.empty(), l);
    e.submit(TestMessages.pacs008());
    e.submit(credit("10.00"));
    c.advance(Duration.ofMinutes(1));
    e.tick();
    return e.payments() + "|" + l.entries();
  }
}
