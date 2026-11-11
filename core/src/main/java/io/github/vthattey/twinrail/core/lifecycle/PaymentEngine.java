package io.github.vthattey.twinrail.core.lifecycle;

import io.github.vthattey.twinrail.core.clock.VirtualClock;
import io.github.vthattey.twinrail.core.ids.SeededIds;
import io.github.vthattey.twinrail.core.ledger.Ledger;
import io.github.vthattey.twinrail.core.matcher.PathExpr;
import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import io.github.vthattey.twinrail.core.rules.RuleEngine;
import io.github.vthattey.twinrail.core.rules.RulePack;
import io.github.vthattey.twinrail.core.rules.Violation;
import io.github.vthattey.twinrail.core.scenario.Action;
import io.github.vthattey.twinrail.core.scenario.Scenario;
import io.github.vthattey.twinrail.core.scenario.ScenarioSet;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs payments through their life: rules, then scenarios, then a funds reservation, then
 * settlement or time-out as the virtual clock moves.
 *
 * <p>The engine never moves the clock. After the clock moves, call {@link #tick()} to apply
 * everything that has become due. Each change is booked at the time it was due, not the time of the
 * tick, so jumping the clock by an hour gives the same ledger as ticking every second.
 *
 * <p>Only credit transfers ({@code pacs.008}) are handled in v0.1. Not thread-safe on its own: the
 * server serialises calls.
 */
public final class PaymentEngine {
  static final String BUILT_IN_SOURCE = "Twinrail built-in check";

  private static final String ROOT = "/Document/FIToFICstmrCdtTrf/CdtTrfTxInf[1]";
  private static final PathExpr AMOUNT = PathExpr.parse(ROOT + "/IntrBkSttlmAmt");
  private static final PathExpr CURRENCY = PathExpr.parse(ROOT + "/IntrBkSttlmAmt/@Ccy");
  private static final PathExpr END_TO_END = PathExpr.parse(ROOT + "/PmtId/EndToEndId");
  private static final PathExpr UETR = PathExpr.parse(ROOT + "/PmtId/UETR");
  private static final List<PathExpr> DEBTOR_ACCOUNT =
      List.of(
          PathExpr.parse(ROOT + "/DbtrAcct/Id/IBAN"),
          PathExpr.parse(ROOT + "/DbtrAcct/Id/Othr/Id"),
          PathExpr.parse(ROOT + "/DbtrAgt/FinInstnId/ClrSysMmbId/MmbId"));
  private static final List<PathExpr> CREDITOR_ACCOUNT =
      List.of(
          PathExpr.parse(ROOT + "/CdtrAcct/Id/IBAN"),
          PathExpr.parse(ROOT + "/CdtrAcct/Id/Othr/Id"),
          PathExpr.parse(ROOT + "/CdtrAgt/FinInstnId/ClrSysMmbId/MmbId"));

  private final Duration timeout;
  private final VirtualClock clock;
  private final SeededIds ids;
  private final RuleEngine rules = new RuleEngine();
  private final RulePack pack;
  private final ScenarioSet scenarios;
  private final Ledger ledger;
  private final Map<String, Payment> payments = new LinkedHashMap<>();
  private final List<Transition> history = new ArrayList<>();

  /**
   * @param timeout how long an accepted payment may stay unsettled before it times out; this is a
   *     test setting, not a scheme value
   */
  public PaymentEngine(
      Duration timeout,
      VirtualClock clock,
      SeededIds ids,
      RulePack pack,
      ScenarioSet scenarios,
      Ledger ledger) {
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException(
          "The time-out must be greater than zero (got " + timeout + "). Use for example PT30S.");
    }
    this.timeout = timeout;
    this.clock = clock;
    this.ids = ids;
    this.pack = pack;
    this.scenarios = scenarios;
    this.ledger = ledger;
  }

  /**
   * Receives a credit transfer. The returned payment is in its state after any immediate
   * settlement.
   *
   * @throws IllegalArgumentException if the message is not a {@code pacs.008}
   */
  public Payment submit(XmlMessage message) {
    if (!message.type().startsWith("pacs.008.")) {
      throw new IllegalArgumentException(
          "Message type "
              + message.type()
              + " cannot start a payment. Send a pacs.008 credit transfer.");
    }
    Instant now = clock.instant();
    String id = ids.sequence("PMT");
    String e2e = first(message, END_TO_END);
    String uetr = first(message, UETR);
    String debtor = firstOf(message, DEBTOR_ACCOUNT);
    String creditor = firstOf(message, CREDITOR_ACCOUNT);
    String currency = first(message, CURRENCY);
    BigDecimal amount = parseAmount(first(message, AMOUNT));

    // The pack's rules judge first, so a rejection names the scheme rule when there is one. The
    // built-in checks are a backstop for what the ledger cannot work without.
    Rejection bad = rejectionFromRules(message);
    if (bad == null && (amount == null || amount.signum() <= 0 || currency == null)) {
      bad =
          builtIn(
              "twinrail.fields.amount",
              "AM01",
              "The settlement amount or currency is missing or invalid.");
    } else if (bad == null && (debtor == null || creditor == null)) {
      bad =
          builtIn(
              "twinrail.fields.accounts",
              "FF01",
              "The debtor or creditor cannot be identified. Give each an account id or an agent"
                  + " member id.");
    }
    Scenario matched = null;
    if (bad == null) {
      Optional<Scenario> hit = scenarios.firstMatch(message);
      if (hit.isPresent()) {
        matched = hit.get();
        if (matched.action() instanceof Action.Reject r) {
          bad =
              new Rejection(
                  "scenario:" + matched.id(),
                  r.reason(),
                  r.text() == null ? "Rejected by scenario " + matched.id() + "." : r.text(),
                  "Twinrail scenario " + matched.id());
        }
      }
    }
    if (bad == null) {
      bad = reserve(debtor, amount, currency);
    }

    if (bad != null) {
      Payment rejected =
          new Payment(
              id,
              message.type(),
              e2e,
              uetr,
              debtor,
              creditor,
              amount,
              currency,
              PaymentState.REJECTED,
              now,
              null,
              now,
              now,
              bad);
      payments.put(id, rejected);
      history.add(new Transition(id, null, PaymentState.REJECTED, now));
      return rejected;
    }

    Instant settleAt = now;
    if (matched != null && matched.action() instanceof Action.Delay d) {
      settleAt = now.plus(d.by());
    } else if (matched != null && matched.action() instanceof Action.Drop) {
      settleAt = null;
    }
    Payment accepted =
        new Payment(
            id,
            message.type(),
            e2e,
            uetr,
            debtor,
            creditor,
            amount,
            currency,
            PaymentState.ACCEPTED,
            now,
            settleAt,
            now.plus(timeout),
            null,
            null);
    payments.put(id, accepted);
    history.add(new Transition(id, null, PaymentState.ACCEPTED, now));
    tick();
    return payments.get(id);
  }

  /** Applies every settlement and time-out that is due at the current virtual time. */
  public List<Transition> tick() {
    Instant now = clock.instant();
    record Due(Payment payment, Instant at, PaymentState to) {}
    List<Due> due = new ArrayList<>();
    for (Payment p : payments.values()) {
      if (p.state() != PaymentState.ACCEPTED) {
        continue;
      }
      boolean settles = p.settleAt() != null && p.settleAt().isBefore(p.timeoutAt());
      if (settles && !p.settleAt().isAfter(now)) {
        due.add(new Due(p, p.settleAt(), PaymentState.SETTLED));
      } else if (!settles && !p.timeoutAt().isAfter(now)) {
        due.add(new Due(p, p.timeoutAt(), PaymentState.TIMED_OUT));
      }
    }
    due.sort(Comparator.comparing(Due::at));
    List<Transition> done = new ArrayList<>();
    for (Due d : due) {
      Payment p = d.payment();
      if (d.to() == PaymentState.SETTLED) {
        ledger.settle(
            d.at(),
            p.id(),
            p.endToEndId(),
            p.debtorAccount(),
            p.creditorAccount(),
            p.amount(),
            p.currency());
      } else {
        ledger.release(p.debtorAccount(), p.amount());
      }
      payments.put(p.id(), p.closed(d.to(), d.at()));
      Transition t = new Transition(p.id(), PaymentState.ACCEPTED, d.to(), d.at());
      history.add(t);
      done.add(t);
    }
    return done;
  }

  public Optional<Payment> find(String id) {
    return Optional.ofNullable(payments.get(id));
  }

  /** All payments in the order they were received. */
  public List<Payment> payments() {
    return List.copyOf(payments.values());
  }

  /** Every state change so far, in the order it was recorded. */
  public List<Transition> history() {
    return List.copyOf(history);
  }

  private Rejection rejectionFromRules(XmlMessage message) {
    Optional<Violation> v = rules.evaluate(pack, message).firstError();
    return v.map(x -> new Rejection(x.ruleId(), x.reason(), x.text(), x.source().toString()))
        .orElse(null);
  }

  private Rejection reserve(String debtor, BigDecimal amount, String currency) {
    return switch (ledger.reserve(debtor, amount, currency)) {
      case OK -> null;
      case INSUFFICIENT_FUNDS ->
          builtIn(
              "twinrail.ledger.funds",
              "AM04",
              "Account "
                  + debtor
                  + " has insufficient available funds for "
                  + amount
                  + " "
                  + currency
                  + ".");
      case WRONG_CURRENCY ->
          builtIn(
              "twinrail.ledger.currency",
              "AM03",
              "Account " + debtor + " does not hold " + currency + ".");
    };
  }

  private static Rejection builtIn(String ruleId, String reason, String text) {
    return new Rejection(ruleId, reason, text, BUILT_IN_SOURCE);
  }

  private static String first(XmlMessage message, PathExpr path) {
    List<String> values = message.values(path);
    return values.isEmpty() || values.get(0).isEmpty() ? null : values.get(0);
  }

  private static String firstOf(XmlMessage message, List<PathExpr> paths) {
    for (PathExpr p : paths) {
      String v = first(message, p);
      if (v != null) {
        return v;
      }
    }
    return null;
  }

  private static BigDecimal parseAmount(String text) {
    try {
      return text == null ? null : new BigDecimal(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
