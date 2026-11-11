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
      // A LinkedHashMap, not Map.of: Map.of iterates in an order that changes from one JVM run to
      // the next, and the same inputs must give byte-identical responses.
      Map<String, Object> rejection = new LinkedHashMap<>();
      rejection.put("rule", r.ruleId());
      rejection.put("reason", r.reason());
      rejection.put("text", r.text());
      rejection.put("source", r.source());
      m.put("rejection", rejection);
    }
    return m;
  }

  /** A webhook target as the API shows it, with a fixed key order. */
  static Map<String, Object> webhook(String id, String url) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("url", url);
    return m;
  }
}
