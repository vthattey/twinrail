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
import io.github.vthattey.twinrail.core.nacha.AchEngine;
import io.github.vthattey.twinrail.core.nacha.AchModel;
import io.github.vthattey.twinrail.core.nacha.ReasonCodes;
import io.github.vthattey.twinrail.core.recorder.Direction;
import io.github.vthattey.twinrail.core.recorder.Recorder;
import io.github.vthattey.twinrail.core.recorder.Session;
import io.github.vthattey.twinrail.core.rules.RulePack;
import io.github.vthattey.twinrail.core.scenario.ScenarioSet;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * All emulator state behind one lock: clock, ids, ledger, payments, scenarios and the recorder. The
 * HTTP layer calls this and nothing else, so every request sees a consistent world and a reset
 * replaces everything at once.
 */
public final class Emulator {
  /** Start-up settings. Same config and same calls give the same results. */
  public record Config(
      long seed, Instant start, Duration timeout, RulePack pack, ReasonCodes codes) {
    /** With the bundled ACH return and change code table. */
    public Config(long seed, Instant start, Duration timeout, RulePack pack) {
      this(seed, start, timeout, pack, defaultCodes());
    }
  }

  /** The ACH code table shipped in the rule-packs module. */
  public static ReasonCodes defaultCodes() {
    try (var in = Emulator.class.getResourceAsStream("/codes/nacha-codes-1.yaml")) {
      if (in == null) {
        throw new IllegalStateException(
            "The ACH code table is missing from the build. Check that the rule-packs module is on the class path.");
      }
      return ReasonCodes.load(
          new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
          "nacha-codes-1.yaml");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot read the ACH code table.", e);
    }
  }

  /** What one clock move caused: payment and ACH state changes. */
  public record Ticked(List<Transition> payments, List<AchModel.Transition> ach) {}

  private final Config config;
  private final MessageCodec codec = new MessageCodec();
  private VirtualClock clock;
  private SeededIds ids;
  private Ledger ledger;
  private Recorder recorder;
  private ScenarioSet scenarios;
  private PaymentEngine engine;
  private AchEngine ach;
  private Statements statements;
  private Webhooks webhooks;
  private int eventCursor;

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
    webhooks = new Webhooks();
    eventCursor = 0;
    rebuild();
  }

  private void rebuild() {
    engine = new PaymentEngine(config.timeout(), clock, ids, config.pack(), scenarios, ledger);
    statements = new Statements(ledger, clock, ids, codec);
    ach = new AchEngine(AchEngine.Config.defaults(), clock, ids, scenarios, ledger, config.codes());
  }

  /** Receives a message: records it and, if it is a credit transfer, runs the payment. */
  public synchronized Payment submit(String xml) {
    var parsed = codec.parse(xml);
    recorder.recordMessage(clock.instant(), Direction.INBOUND, parsed.type(), xml);
    Payment payment = engine.submit(XmlMessage.of(parsed, codec));
    syncEvents();
    return payment;
  }

  public synchronized Instant now() {
    return clock.instant();
  }

  public synchronized Ticked advance(Duration by) {
    Instant after = clock.advance(by);
    recorder.recordClockAdvanced(after, by);
    return tickAll();
  }

  public synchronized Ticked setClock(Instant to) {
    Instant after = clock.set(to);
    recorder.recordClockSet(after);
    return tickAll();
  }

  private Ticked tickAll() {
    var payments = engine.tick();
    var achDone = ach.tick();
    syncEvents();
    return new Ticked(payments, achDone);
  }

  /** Receives an ACH file. Structural errors throw; wrong control totals refuse the whole file. */
  public synchronized AchModel.Result submitAch(String text) {
    recorder.recordMessage(clock.instant(), Direction.INBOUND, "nacha.file", text);
    return ach.submit(text);
  }

  public synchronized List<AchModel.Entry> achEntries() {
    return ach.entries();
  }

  public synchronized Optional<AchModel.Entry> achEntry(String id) {
    return ach.find(id);
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
    ach.setScenarios(set); // validates ACH codes first; nothing changes if it throws
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

  // Webhooks. Events come from the payment history, so each state change is announced once.

  private void syncEvents() {
    var history = engine.history();
    for (; eventCursor < history.size(); eventCursor++) {
      Transition t = history.get(eventCursor);
      Map<String, Object> payload =
          new LinkedHashMap<>(PaymentJson.of(engine.find(t.paymentId()).orElseThrow()));
      payload.put("state", t.to().name());
      webhooks.publish("payment." + t.to().name().toLowerCase(Locale.ROOT), t.at(), payload);
    }
  }

  public synchronized Webhooks.Target addWebhook(String url, String secret) {
    return webhooks.register(url, secret);
  }

  public synchronized boolean removeWebhook(String id) {
    return webhooks.remove(id);
  }

  public synchronized List<Webhooks.Target> webhookTargets() {
    return webhooks.targets();
  }

  public synchronized List<Map<String, Object>> deliveries() {
    return webhooks.deliveries().stream().map(Webhooks.Delivery::view).toList();
  }

  /**
   * Sends every delivery that is due at the current virtual time. The HTTP calls happen outside the
   * lock; failed ones are rescheduled on the virtual clock, so a retry needs the clock to move.
   */
  public void deliverDue(WebhookSender sender) {
    List<Webhooks.Delivery> due;
    Instant now;
    synchronized (this) {
      now = clock.instant();
      due = webhooks.due(now);
    }
    for (Webhooks.Delivery d : due) {
      int status = sender.post(URI.create(d.target.url()), Webhooks.headers(d), d.body);
      synchronized (this) {
        webhooks.record(d, status, now);
      }
    }
  }

  /** An account as the control API shows it. */
  public record AccountView(String id, String currency, BigDecimal balance, BigDecimal available) {}
}
