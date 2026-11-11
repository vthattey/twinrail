package io.github.vthattey.twinrail.server;

import io.github.vthattey.twinrail.core.lifecycle.Payment;
import io.github.vthattey.twinrail.core.lifecycle.PaymentEngine;
import io.github.vthattey.twinrail.core.lifecycle.PaymentState;
import io.github.vthattey.twinrail.core.nacha.AchEngine;
import io.github.vthattey.twinrail.core.nacha.AchModel;
import io.github.vthattey.twinrail.core.recorder.RecordedEvent;
import io.github.vthattey.twinrail.ui.Snapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns emulator state into the plain data the inspector renders: a timeline of messages, clock
 * moves, payment and ACH state changes (newest first), accounts and webhook deliveries.
 */
final class UiSnapshots {
  private UiSnapshots() {}

  static Snapshot build(
      Instant now,
      List<RecordedEvent> events,
      PaymentEngine payments,
      AchEngine ach,
      List<Emulator.AccountView> accounts,
      List<Webhooks.Delivery> deliveries) {
    // Assembled with messages and clock moves first, so that after a stable sort by time a message
    // comes before the state changes it caused.
    List<Snapshot.Row> rows = new ArrayList<>();
    for (RecordedEvent e : events) {
      rows.add(recorded(e));
    }
    for (var t : payments.history()) {
      payments.find(t.paymentId()).ifPresent(p -> rows.add(payment(t.to(), t.at(), p)));
    }
    for (var t : ach.history()) {
      ach.find(t.entryId()).ifPresent(e -> rows.add(ach(t, e)));
    }
    rows.sort(Comparator.comparing(Snapshot.Row::at));
    java.util.Collections.reverse(rows);

    return new Snapshot(
        now,
        accounts.stream()
            .map(a ->
                new Snapshot.Account(
                    a.id(),
                    a.currency(),
                    a.balance().toPlainString(),
                    a.available().toPlainString()))
            .toList(),
        rows,
        deliveries.stream()
            .map(d ->
                new Snapshot.Delivery(
                    d.id,
                    d.target.id(),
                    d.eventId,
                    d.state,
                    d.attempts,
                    "PENDING".equals(d.state) ? d.nextAttemptAt.toString() : null))
            .toList());
  }

  private static Snapshot.Row recorded(RecordedEvent e) {
    return switch (e) {
      case RecordedEvent.Message m ->
          new Snapshot.Row(
              "m-" + m.seq(),
              m.at(),
              "message",
              m.type() + " " + m.direction().name().toLowerCase(java.util.Locale.ROOT),
              null,
              List.of(),
              null,
              m.payload());
      case RecordedEvent.ClockAdvanced c ->
          new Snapshot.Row(
              "c-" + c.seq(),
              c.at(),
              "clock",
              "Clock advanced by " + c.by(),
              null,
              List.of(new Snapshot.Field("Time now", c.at().toString())),
              null,
              null);
      case RecordedEvent.ClockSet c ->
          new Snapshot.Row(
              "c-" + c.seq(),
              c.at(),
              "clock",
              "Clock set",
              null,
              List.of(new Snapshot.Field("Time now", c.at().toString())),
              null,
              null);
    };
  }

  private static Snapshot.Row payment(PaymentState to, Instant at, Payment p) {
    List<Snapshot.Field> fields = new ArrayList<>();
    fields.add(new Snapshot.Field("Debtor", String.valueOf(p.debtorAccount())));
    fields.add(new Snapshot.Field("Creditor", String.valueOf(p.creditorAccount())));
    fields.add(new Snapshot.Field("Amount", p.amount() + " " + p.currency()));
    fields.add(new Snapshot.Field("End to end id", String.valueOf(p.endToEndId())));
    fields.add(new Snapshot.Field("UETR", String.valueOf(p.uetr())));
    Snapshot.Rule rule = null;
    switch (to) {
      case ACCEPTED -> {
        if (p.settleAt() != null) {
          fields.add(new Snapshot.Field("Settles at", p.settleAt().toString()));
        } else {
          fields.add(new Snapshot.Field("Settles at", "never (a scenario drops it)"));
        }
        fields.add(new Snapshot.Field("Times out at", p.timeoutAt().toString()));
      }
      case TIMED_OUT -> fields.add(new Snapshot.Field("Timed out at", p.timeoutAt().toString()));
      case REJECTED -> {
        var r = p.rejection();
        rule = new Snapshot.Rule(r.ruleId(), r.reason(), r.text(), r.source());
      }
      default -> {}
    }
    String verb =
        to == PaymentState.TIMED_OUT ? "timed out" : to.name().toLowerCase(java.util.Locale.ROOT);
    return new Snapshot.Row(
        "p-" + p.id() + "-" + to.name(),
        at,
        "payment",
        p.id() + " " + verb,
        to.name(),
        fields,
        rule,
        null);
  }

  private static Snapshot.Row ach(AchModel.Transition t, AchModel.Entry e) {
    List<Snapshot.Field> fields = new ArrayList<>();
    fields.add(new Snapshot.Field("Account", e.accountNumber()));
    fields.add(new Snapshot.Field("Routing number", e.routingNumber()));
    fields.add(
        new Snapshot.Field(
            "Amount", e.amount() + " (transaction code " + e.transactionCode() + ")"));
    fields.add(new Snapshot.Field("Name", e.individualName()));
    fields.add(new Snapshot.Field("Trace number", e.traceNumber()));
    fields.add(new Snapshot.Field("Company", e.companyName() + " (" + e.secCode() + ")"));
    Snapshot.Rule rule = null;
    String payload = null;
    String title = e.id() + " " + t.kind();
    String status = t.to().name();
    if ("returned".equals(t.kind()) && e.returned() != null) {
      var r = e.returned();
      title = e.id() + " returned " + r.code();
      rule =
          new Snapshot.Rule(
              "ach.return",
              r.code(),
              r.title() + ". Return time frame per the source: " + r.timeframe() + ".",
              r.source());
      payload = r.addendaRecord();
    } else if ("change".equals(t.kind()) && e.change() != null) {
      var c = e.change();
      title = e.id() + " change notification " + c.code();
      fields.add(new Snapshot.Field("Change", c.code() + " " + c.title()));
      fields.add(new Snapshot.Field("Corrected values", c.corrected().toString()));
      fields.add(new Snapshot.Field("Source", c.source()));
      payload = c.addendaRecord();
    }
    return new Snapshot.Row(
        "a-" + e.id() + "-" + t.kind(), t.at(), "ach", title, status, fields, rule, payload);
  }
}
