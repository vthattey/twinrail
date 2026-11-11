package io.github.vthattey.twinrail.server;

import io.github.vthattey.twinrail.core.lifecycle.Payment;
import io.github.vthattey.twinrail.core.lifecycle.Rejection;
import java.util.LinkedHashMap;
import java.util.Map;

/** The JSON shape of a payment, shared by the REST responses and webhook payloads. */
final class PaymentJson {
  private PaymentJson() {}

  static Map<String, Object> of(Payment p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.id());
    m.put("state", p.state().name());
    m.put("endToEndId", p.endToEndId());
    m.put("uetr", p.uetr());
    m.put("debtorAccount", p.debtorAccount());
    m.put("creditorAccount", p.creditorAccount());
    m.put("amount", p.amount() == null ? null : p.amount().toPlainString());
    m.put("currency", p.currency());
    m.put("receivedAt", p.receivedAt().toString());
    m.put("settleAt", p.settleAt() == null ? null : p.settleAt().toString());
    m.put("timeoutAt", p.timeoutAt().toString());
    m.put("closedAt", p.closedAt() == null ? null : p.closedAt().toString());
    Rejection r = p.rejection();
    if (r != null) {
      m.put(
          "rejection",
          Map.of("rule", r.ruleId(), "reason", r.reason(), "text", r.text(), "source", r.source()));
    }
    return m;
  }
}
