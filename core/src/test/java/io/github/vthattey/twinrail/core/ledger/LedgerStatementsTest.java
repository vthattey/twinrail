package io.github.vthattey.twinrail.core.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.clock.VirtualClock;
import io.github.vthattey.twinrail.core.codec.MessageCodec;
import io.github.vthattey.twinrail.core.ids.SeededIds;
import io.github.vthattey.twinrail.core.matcher.PathExpr;
import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class LedgerStatementsTest {
  private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");
  private static final MessageCodec CODEC = new MessageCodec();
  private static final String STMT = "/Document/BkToCstmrStmt/Stmt";

  private static List<String> values(XmlMessage m, String path) {
    return m.values(PathExpr.parse(path));
  }

  /** Random settled payments between a few accounts: conservation and statement consistency. */
  @Test
  void randomActivityKeepsTotalsAndStatementsConsistent() {
    for (long seed = 1; seed <= 25; seed++) {
      var random = new SplittableRandom(seed);
      var ledger = new Ledger();
      var clock = new VirtualClock(START);
      var ids = new SeededIds(seed);
      String[] accounts = {"A", "B", "C", "D"};
      for (String a : accounts) {
        ledger.open(a, "CAD", new BigDecimal(random.nextInt(1000, 5000)));
      }
      BigDecimal opening = ledger.total();

      for (int i = 0; i < 60; i++) {
        String from = accounts[random.nextInt(accounts.length)];
        String to = accounts[random.nextInt(accounts.length)];
        BigDecimal amount = BigDecimal.valueOf(random.nextInt(1, 20000), 2);
        if (!from.equals(to) && ledger.reserve(from, amount, "CAD") == Ledger.Reservation.OK) {
          clock.advance(Duration.ofSeconds(random.nextInt(1, 100)));
          ledger.settle(clock.instant(), ids.sequence("PMT"), "E" + i, from, to, amount, "CAD");
        }
      }
      assertEquals(0, opening.compareTo(ledger.total()), "money is conserved, seed " + seed);

      var statements = new Statements(ledger, clock, ids, CODEC);
      for (String a : accounts) {
        XmlMessage s = XmlMessage.of(CODEC.parse(statements.camt053(a)), CODEC);
        assertEquals("camt.053.001.08", s.type());

        BigDecimal net = BigDecimal.ZERO;
        for (Entry e : ledger.entries(a)) {
          net = net.add(e.side() == Side.CREDIT ? e.amount() : e.amount().negate());
        }
        assertEquals(ledger.balance(a), ledger.openingBalance(a).add(net), "seed " + seed);

        // What the statement says must add up on its own: opening + credits - debits = closing.
        List<String> amounts = values(s, STMT + "/Ntry/Amt");
        List<String> sides = values(s, STMT + "/Ntry/CdtDbtInd");
        assertEquals(ledger.entries(a).size(), amounts.size(), "seed " + seed);
        BigDecimal computed = new BigDecimal(values(s, STMT + "/Bal[1]/Amt").get(0));
        for (int i = 0; i < amounts.size(); i++) {
          BigDecimal v = new BigDecimal(amounts.get(i));
          computed = sides.get(i).equals("CRDT") ? computed.add(v) : computed.subtract(v);
        }
        assertEquals(
            0,
            ledger.balance(a).compareTo(computed),
            "closing balance in statement for " + a + ", seed " + seed);
        assertEquals(
            0,
            ledger.balance(a).compareTo(new BigDecimal(values(s, STMT + "/Bal[2]/Amt").get(0))),
            "closing balance field, seed " + seed);
      }
    }
  }

  @Test
  void notificationMatchesTheBooking() {
    var ledger = new Ledger();
    var clock = new VirtualClock(START);
    ledger.open("A", "CAD", new BigDecimal("100.00"));
    ledger.reserve("A", new BigDecimal("40.25"), "CAD");
    ledger.settle(START, "PMT-000001", "E2E-1", "A", "B", new BigDecimal("40.25"), "CAD");
    var statements = new Statements(ledger, clock, new SeededIds(1), CODEC);

    List<Entry> entries = ledger.entries();
    XmlMessage debit = XmlMessage.of(CODEC.parse(statements.camt054(entries.get(0))), CODEC);
    XmlMessage credit = XmlMessage.of(CODEC.parse(statements.camt054(entries.get(1))), CODEC);

    assertEquals("camt.054.001.08", debit.type());
    String base = "/Document/BkToCstmrDbtCdtNtfctn/Ntfctn";
    assertEquals(List.of("DBIT"), values(debit, base + "/Ntry/CdtDbtInd"));
    assertEquals(List.of("CRDT"), values(credit, base + "/Ntry/CdtDbtInd"));
    assertEquals(List.of("40.25"), values(debit, base + "/Ntry/Amt"));
    assertEquals(List.of("E2E-1"), values(credit, base + "/Ntry/NtryDtls/TxDtls/Refs/EndToEndId"));
    assertEquals(List.of("B"), values(credit, base + "/Acct/Id/Othr/Id"));
  }

  @Test
  void statementAndNotificationMatchGoldenFiles() {
    var ledger = new Ledger();
    ledger.open("A", "CAD", new BigDecimal("100.00"));
    ledger.reserve("A", new BigDecimal("10.00"), "CAD");
    ledger.settle(START, "PMT-000001", "E1", "A", "B", new BigDecimal("10.00"), "CAD");
    var statements = new Statements(ledger, new VirtualClock(START), new SeededIds(3), CODEC);

    assertEquals(
        io.github.vthattey.twinrail.core.TestMessages.resource("/ledger/camt053-golden.xml"),
        statements.camt053("A"));
    assertEquals(
        io.github.vthattey.twinrail.core.TestMessages.resource("/ledger/camt054-golden.xml"),
        statements.camt054(ledger.entries().get(1)));
  }

  @Test
  void statementIsDeterministic() {
    var out = new String[2];
    for (int run = 0; run < 2; run++) {
      var ledger = new Ledger();
      ledger.open("A", "CAD", new BigDecimal("100.00"));
      ledger.reserve("A", new BigDecimal("10.00"), "CAD");
      ledger.settle(START, "PMT-000001", "E1", "A", "B", new BigDecimal("10.00"), "CAD");
      out[run] =
          new Statements(ledger, new VirtualClock(START), new SeededIds(3), CODEC).camt053("A");
    }
    assertEquals(out[0], out[1]);
  }

  @Test
  void openedAccountsCannotBeOverdrawn() {
    var ledger = new Ledger();
    ledger.open("A", "CAD", new BigDecimal("5.00"));
    assertEquals(
        Ledger.Reservation.INSUFFICIENT_FUNDS, ledger.reserve("A", new BigDecimal("5.01"), "CAD"));
    assertEquals(Ledger.Reservation.OK, ledger.reserve("A", new BigDecimal("5.00"), "CAD"));
    assertEquals(
        Ledger.Reservation.INSUFFICIENT_FUNDS, ledger.reserve("A", new BigDecimal("0.01"), "CAD"));
  }

  @Test
  void unknownAccountsAreCreatedAndMayOverdraw() {
    var ledger = new Ledger();
    assertEquals(Ledger.Reservation.OK, ledger.reserve("X", new BigDecimal("1000"), "CAD"));
    assertTrue(ledger.has("X"));
  }

  @Test
  void misuseExplainsWhatToDo() {
    var ledger = new Ledger();
    ledger.open("A", "CAD", BigDecimal.ONE);
    var again = assertThrows(LedgerException.class, () -> ledger.open("A", "CAD", BigDecimal.ONE));
    assertTrue(again.getMessage().contains("already open"));
    var missing = assertThrows(LedgerException.class, () -> ledger.balance("Z"));
    assertTrue(missing.getMessage().contains("does not exist"));
  }
}
