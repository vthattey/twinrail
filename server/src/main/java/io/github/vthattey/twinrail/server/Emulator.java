package io.github.vthattey.twinrail.server;

import io.github.vthattey.twinrail.core.clock.VirtualClock;
import io.github.vthattey.twinrail.core.codec.MessageCodec;
import io.github.vthattey.twinrail.core.ids.SeededIds;
import io.github.vthattey.twinrail.core.ledger.Ledger;
import io.github.vthattey.twinrail.core.ledger.Statements;
import io.github.vthattey.twinrail.core.lifecycle.Payment;
import io.github.vthattey.twinrail.core.lifecycle.PaymentEngine;
import io.github.vthattey.twinrail.core.lifecycle.Transition;
import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import io.github.vthattey.twinrail.core.recorder.Direction;
import io.github.vthattey.twinrail.core.recorder.Recorder;
import io.github.vthattey.twinrail.core.recorder.Session;
import io.github.vthattey.twinrail.core.rules.RulePack;
import io.github.vthattey.twinrail.core.scenario.ScenarioSet;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * All emulator state behind one lock: clock, ids, ledger, payments, scenarios and the recorder. The
 * HTTP layer calls this and nothing else, so every request sees a consistent world and a reset
 * replaces everything at once.
 */
public final class Emulator {
  /** Start-up settings. Same config and same calls give the same results. */
  public record Config(long seed, Instant start, Duration timeout, RulePack pack) {}

  private final Config config;
  private final MessageCodec codec = new MessageCodec();
  private VirtualClock clock;
  private SeededIds ids;
  private Ledger ledger;
  private Recorder recorder;
  private ScenarioSet scenarios;
  private PaymentEngine engine;
  private Statements statements;

  public Emulator(Config config) {
    this.config = config;
    reset();
  }

  public synchronized void reset() {
    clock = new VirtualClock(config.start());
    ids = new SeededIds(config.seed());
    ledger = new Ledger();
    recorder = new Recorder(config.seed(), config.start());
    scenarios = ScenarioSet.empty();
    rebuild();
  }

  private void rebuild() {
    engine = new PaymentEngine(config.timeout(), clock, ids, config.pack(), scenarios, ledger);
    statements = new Statements(ledger, clock, ids, codec);
  }

  /** Receives a message: records it and, if it is a credit transfer, runs the payment. */
  public synchronized Payment submit(String xml) {
    var parsed = codec.parse(xml);
    recorder.recordMessage(clock.instant(), Direction.INBOUND, parsed.type(), xml);
    return engine.submit(XmlMessage.of(parsed, codec));
  }

  public synchronized Instant now() {
    return clock.instant();
  }

  public synchronized List<Transition> advance(Duration by) {
    Instant after = clock.advance(by);
    recorder.recordClockAdvanced(after, by);
    return engine.tick();
  }

  public synchronized List<Transition> setClock(Instant to) {
    Instant after = clock.set(to);
    recorder.recordClockSet(after);
    return engine.tick();
  }

  public synchronized void openAccount(String id, String currency, BigDecimal balance) {
    ledger.open(id, currency, balance);
  }

  public synchronized Optional<AccountView> account(String id) {
    if (!ledger.has(id)) {
      return Optional.empty();
    }
    return Optional.of(
        new AccountView(id, ledger.currency(id), ledger.balance(id), ledger.available(id)));
  }

  public synchronized List<AccountView> accounts() {
    return ledger.accountIds().stream().map(id -> account(id).orElseThrow()).toList();
  }

  public synchronized String statement(String account) {
    return statements.camt053(account);
  }

  /** Replaces the scenario set for payments received from now on. */
  public synchronized void setScenarios(ScenarioSet set) {
    scenarios = set;
    engine.setScenarios(set);
  }

  public synchronized ScenarioSet scenarios() {
    return scenarios;
  }

  public synchronized List<Payment> payments() {
    return engine.payments();
  }

  public synchronized Optional<Payment> payment(String id) {
    return engine.find(id);
  }

  public synchronized Session session() {
    return recorder.session();
  }

  /** An account as the control API shows it. */
  public record AccountView(String id, String currency, BigDecimal balance, BigDecimal available) {}
}
