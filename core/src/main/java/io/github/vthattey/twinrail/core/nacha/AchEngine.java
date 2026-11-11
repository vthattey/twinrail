package io.github.vthattey.twinrail.core.nacha;

import io.github.vthattey.twinrail.core.clock.VirtualClock;
import io.github.vthattey.twinrail.core.definition.DefinitionException;
import io.github.vthattey.twinrail.core.ids.SeededIds;
import io.github.vthattey.twinrail.core.ledger.Ledger;
import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import io.github.vthattey.twinrail.core.nacha.AchModel.Change;
import io.github.vthattey.twinrail.core.nacha.AchModel.Entry;
import io.github.vthattey.twinrail.core.nacha.AchModel.Result;
import io.github.vthattey.twinrail.core.nacha.AchModel.State;
import io.github.vthattey.twinrail.core.nacha.AchModel.Transition;
import io.github.vthattey.twinrail.core.scenario.Action;
import io.github.vthattey.twinrail.core.scenario.Scenario;
import io.github.vthattey.twinrail.core.scenario.ScenarioSet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Receives ACH files and runs their entries through settlement, returns and notifications of change
 * on the virtual clock. See {@code docs/nacha.md} and ADR 0011.
 *
 * <p>Like {@code PaymentEngine}, it never moves the clock: after the clock moves, call {@link
 * #tick()}. Each event is booked at its due time. Not thread-safe on its own.
 */
public final class AchEngine {
  /**
   * @param currency the currency of every entry (an ACH file carries none)
   * @param settlementAccount the system account that is the other side of every entry
   */
  public record Config(String currency, String settlementAccount) {
    public static Config defaults() {
      return new Config("USD", "ACH-SETTLEMENT");
    }
  }

  /** What is still to happen to an entry. */
  private record Plan(
      Instant settleAt, Action.Return returnAction, Action.Noc nocAction, long sequence) {}

  private final Config config;
  private final VirtualClock clock;
  private final SeededIds ids;
  private final Ledger ledger;
  private final ReasonCodes codes;
  private ScenarioSet scenarios;
  private final Map<String, Entry> entries = new LinkedHashMap<>();
  private final Map<String, Plan> plans = new LinkedHashMap<>();
  private final List<Transition> history = new ArrayList<>();
  private long sequence;

  public AchEngine(
      Config config,
      VirtualClock clock,
      SeededIds ids,
      ScenarioSet scenarios,
      Ledger ledger,
      ReasonCodes codes) {
    this.config = config;
    this.clock = clock;
    this.ids = ids;
    this.scenarios = scenarios;
    this.ledger = ledger;
    this.codes = codes;
  }

  /**
   * Checks that every ACH action in the scenarios uses a code the table knows and corrected values
   * that fit the record. Call before using a scenario set.
   *
   * @throws DefinitionException with what to change
   */
  public static void validate(ScenarioSet set, ReasonCodes codes) {
    for (Scenario s : set.scenarios()) {
      if (s.action() instanceof Action.Return r && codes.returnCode(r.code()) == null) {
        throw new DefinitionException(
            "Scenario "
                + s.id()
                + ": return code "
                + r.code()
                + " is not in the code table. Known: "
                + codes.returnCodes());
      }
      if (s.action() instanceof Action.Noc n) {
        if (codes.changeCode(n.code()) == null) {
          throw new DefinitionException(
              "Scenario "
                  + s.id()
                  + ": change code "
                  + n.code()
                  + " is not in the code table. Known: "
                  + codes.changeCodes());
        }
        try {
          CorrectedData.format(n.code(), n.corrected());
        } catch (NachaException e) {
          throw new DefinitionException("Scenario " + s.id() + ": " + e.getMessage(), e);
        }
      }
    }
  }

  /** Replaces the scenarios used for entries received from now on. */
  public void setScenarios(ScenarioSet set) {
    validate(set, codes);
    this.scenarios = set;
  }

  /**
   * Receives a file. A file with structural problems throws {@link NachaException}; one whose
   * control totals or header values are wrong is refused whole and reported in the result.
   */
  public Result submit(String text) {
    NachaFile file = NachaCodec.parse(text);
    var problems = NachaCheck.verify(file);
    if (!problems.isEmpty()) {
      return new Result(problems, List.of());
    }
    Instant now = clock.instant();
    List<String> created = new ArrayList<>();
    for (NachaFile.Batch batch : file.batches()) {
      for (NachaFile.Entry e : batch.entries()) {
        created.add(accept(batch.header(), e.detail(), now));
      }
    }
    tick();
    return new Result(List.of(), created.stream().map(entries::get).toList());
  }

  private String accept(NachaRecord batch, NachaRecord detail, Instant now) {
    String id = ids.sequence("ACH");
    String trace = detail.get("traceNumber");
    String code = detail.get("transactionCode");
    boolean credit = NachaAmounts.isCredit(code);
    BigDecimal amount = BigDecimal.valueOf(detail.number("amount"), 2);
    String account = detail.get("accountNumber");
    Entry entry =
        new Entry(
            id,
            trace,
            (int) batch.number("batchNumber"),
            batch.get("companyName"),
            batch.get("secCode"),
            code,
            detail.get("rdfiId") + detail.get("checkDigit"),
            account,
            amount,
            detail.get("individualName"),
            State.ACCEPTED,
            now,
            now,
            null,
            null,
            null);

    Optional<Scenario> hit = scenarios.firstMatch(view(batch, detail, amount));
    Action action = hit.map(Scenario::action).orElse(null);
    Instant settleAt = now;
    Action.Return ret = null;
    Action.Noc noc = null;
    if (action instanceof Action.Delay d) {
      settleAt = now.plus(d.by());
    } else if (action instanceof Action.Return r) {
      ret = r;
    } else if (action instanceof Action.Noc n) {
      noc = n;
    } // reject and drop do not apply to ACH entries (ADR 0011): the entry settles normally

    if (amount.signum() > 0) {
      String payer = credit ? config.settlementAccount() : account;
      var reservation = ledger.reserve(payer, amount, config.currency());
      if (reservation != Ledger.Reservation.OK) {
        String reason = reservation == Ledger.Reservation.INSUFFICIENT_FUNDS ? "R01" : "R03";
        entry = returned(entry, reason, reasonText(reservation), now, now);
        entries.put(id, entry);
        history.add(new Transition(id, null, State.RETURNED, "returned", now));
        return id;
      }
    }
    entries.put(
        id,
        new Entry(
            entry.id(),
            entry.traceNumber(),
            entry.batchNumber(),
            entry.companyName(),
            entry.secCode(),
            entry.transactionCode(),
            entry.routingNumber(),
            entry.accountNumber(),
            entry.amount(),
            entry.individualName(),
            State.ACCEPTED,
            now,
            settleAt,
            null,
            null,
            null));
    plans.put(id, new Plan(settleAt, ret, noc, ++sequence));
    history.add(new Transition(id, null, State.ACCEPTED, "accepted", now));
    return id;
  }

  private static String reasonText(Ledger.Reservation r) {
    return r == Ledger.Reservation.INSUFFICIENT_FUNDS
        ? "Insufficient available funds"
        : "Account does not hold the currency";
  }

  /** Applies every settlement, return and change that is due at the current virtual time. */
  public List<Transition> tick() {
    Instant now = clock.instant();
    List<Transition> done = new ArrayList<>();
    while (true) {
      record Due(Instant at, int kind, long seq, String id) {}
      List<Due> due = new ArrayList<>();
      for (var p : plans.entrySet()) {
        Entry e = entries.get(p.getKey());
        Plan plan = p.getValue();
        if (e.state() == State.ACCEPTED && !plan.settleAt().isAfter(now)) {
          due.add(new Due(plan.settleAt(), 0, plan.sequence(), e.id()));
        } else if (e.state() == State.SETTLED) {
          if (plan.returnAction() != null && !returnAt(e, plan).isAfter(now)) {
            due.add(new Due(returnAt(e, plan), 1, plan.sequence(), e.id()));
          } else if (plan.nocAction() != null
              && e.change() == null
              && !plan.settleAt().isAfter(now)) {
            due.add(new Due(plan.settleAt(), 2, plan.sequence(), e.id()));
          }
        }
      }
      if (due.isEmpty()) {
        return done;
      }
      Due next =
          due.stream()
              .min(
                  Comparator.comparing(Due::at)
                      .thenComparingInt(Due::kind)
                      .thenComparingLong(Due::seq))
              .orElseThrow();
      done.add(apply(next.id(), next.kind(), next.at()));
    }
  }

  private static Instant returnAt(Entry e, Plan plan) {
    return plan.settleAt().plus(plan.returnAction().after());
  }

  private Transition apply(String id, int kind, Instant at) {
    Entry e = entries.get(id);
    Plan plan = plans.get(id);
    boolean credit = NachaAmounts.isCredit(e.transactionCode());
    String payer = credit ? config.settlementAccount() : e.accountNumber();
    String payee = credit ? e.accountNumber() : config.settlementAccount();
    boolean money = e.amount().signum() > 0;
    Transition t;
    switch (kind) {
      case 0 -> {
        if (money) {
          ledger.settle(at, id, e.traceNumber(), payer, payee, e.amount(), config.currency());
        }
        entries.put(id, withState(e, State.SETTLED, null, null, null));
        t = new Transition(id, State.ACCEPTED, State.SETTLED, "settled", at);
      }
      case 1 -> {
        if (money) {
          ledger.book(at, id, e.traceNumber(), payee, payer, e.amount(), config.currency());
        }
        Action.Return r = plan.returnAction();
        entries.put(id, returned(e, r.code(), r.text(), at, at));
        t = new Transition(id, State.SETTLED, State.RETURNED, "returned", at);
      }
      default -> {
        Action.Noc n = plan.nocAction();
        entries.put(id, withState(e, State.SETTLED, null, null, change(e, n, at)));
        t = new Transition(id, State.SETTLED, State.SETTLED, "change", at);
      }
    }
    history.add(t);
    return t;
  }

  private Entry returned(Entry e, String code, String text, Instant at, Instant closed) {
    var c = codes.returnCode(code);
    Map<String, String> addenda = new LinkedHashMap<>();
    addenda.put("typeCode", "99");
    addenda.put("returnCode", code);
    addenda.put("originalTrace", e.traceNumber());
    addenda.put("originalRdfiId", e.routingNumber().substring(0, 8));
    String info = text == null ? "" : text;
    addenda.put("addendaInfo", info.length() > 44 ? info.substring(0, 44) : info); // field width
    addenda.put("traceNumber", e.traceNumber());
    String line = NachaRecord.of(Layouts.ADDENDA_99, '7', addenda).toLine();
    var r =
        new AchModel.Return(code, c.title(), c.timeframe(), c.source().toString(), text, at, line);
    return withState(e, State.RETURNED, closed, r, e.change());
  }

  private Change change(Entry e, Action.Noc n, Instant at) {
    var c = codes.changeCode(n.code());
    Map<String, String> addenda = new LinkedHashMap<>();
    addenda.put("typeCode", "98");
    addenda.put("changeCode", n.code());
    addenda.put("originalTrace", e.traceNumber());
    addenda.put("originalRdfiId", e.routingNumber().substring(0, 8));
    addenda.put("correctedData", CorrectedData.format(n.code(), n.corrected()));
    addenda.put("traceNumber", e.traceNumber());
    String line = NachaRecord.of(Layouts.ADDENDA_98, '7', addenda).toLine();
    return new Change(n.code(), c.title(), c.source().toString(), n.corrected(), at, line);
  }

  private static Entry withState(
      Entry e, State state, Instant closedAt, AchModel.Return ret, Change change) {
    return new Entry(
        e.id(),
        e.traceNumber(),
        e.batchNumber(),
        e.companyName(),
        e.secCode(),
        e.transactionCode(),
        e.routingNumber(),
        e.accountNumber(),
        e.amount(),
        e.individualName(),
        state,
        e.receivedAt(),
        e.settleAt(),
        closedAt == null ? e.closedAt() : closedAt,
        ret == null ? e.returned() : ret,
        change == null ? e.change() : change);
  }

  /** The entry as scenarios see it: {@code nacha.entry} with the elements listed in ADR 0011. */
  private static XmlMessage view(NachaRecord batch, NachaRecord detail, BigDecimal amount) {
    Map<String, String> v = new LinkedHashMap<>();
    v.put("TransactionCode", detail.get("transactionCode"));
    v.put("RoutingNumber", detail.get("rdfiId") + detail.get("checkDigit"));
    v.put("AccountNumber", detail.get("accountNumber"));
    v.put("Amount", amount.toPlainString());
    v.put("IndividualId", detail.get("individualId"));
    v.put("IndividualName", detail.get("individualName"));
    v.put("TraceNumber", detail.get("traceNumber"));
    v.put("SecCode", batch.get("secCode"));
    v.put("CompanyName", batch.get("companyName"));
    v.put("CompanyId", batch.get("companyId"));
    v.put("EntryDescription", batch.get("entryDescription"));
    v.put("EffectiveEntryDate", batch.get("effectiveEntryDate"));
    StringBuilder xml = new StringBuilder("<Entry>");
    v.forEach((k, val) ->
        xml.append('<')
            .append(k)
            .append('>')
            .append(escape(val))
            .append("</")
            .append(k)
            .append('>'));
    return XmlMessage.parse("nacha.entry", xml.append("</Entry>").toString());
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  public Optional<Entry> find(String id) {
    return Optional.ofNullable(entries.get(id));
  }

  /** Entries in the order received. */
  public List<Entry> entries() {
    return List.copyOf(entries.values());
  }

  public List<Transition> history() {
    return List.copyOf(history);
  }
}
