package io.github.vthattey.twinrail.server;

import io.github.vthattey.twinrail.core.nacha.AchModel;
import java.util.LinkedHashMap;
import java.util.Map;

/** The JSON shape of ACH entries and results. Null values are left out. */
final class AchJson {
  private AchJson() {}

  static Map<String, Object> entry(AchModel.Entry e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.id());
    m.put("state", e.state().name());
    m.put("traceNumber", e.traceNumber());
    m.put("batchNumber", e.batchNumber());
    m.put("companyName", e.companyName());
    m.put("secCode", e.secCode());
    m.put("transactionCode", e.transactionCode());
    m.put("routingNumber", e.routingNumber());
    m.put("accountNumber", e.accountNumber());
    m.put("amount", e.amount().toPlainString());
    m.put("individualName", e.individualName());
    m.put("receivedAt", e.receivedAt().toString());
    m.put("settleAt", e.settleAt().toString());
    m.put("closedAt", e.closedAt() == null ? null : e.closedAt().toString());
    var r = e.returned();
    if (r != null) {
      Map<String, Object> ret = new LinkedHashMap<>();
      ret.put("code", r.code());
      ret.put("title", r.title());
      ret.put("timeframe", r.timeframe());
      ret.put("source", r.source());
      ret.put("text", r.text());
      ret.put("at", r.at().toString());
      ret.put("addendaRecord", r.addendaRecord());
      m.put("return", ret);
    }
    var c = e.change();
    if (c != null) {
      Map<String, Object> change = new LinkedHashMap<>();
      change.put("code", c.code());
      change.put("title", c.title());
      change.put("source", c.source());
      change.put("corrected", c.corrected());
      change.put("at", c.at().toString());
      change.put("addendaRecord", c.addendaRecord());
      m.put("change", change);
    }
    return m;
  }

  static Map<String, Object> result(AchModel.Result r) {
    Map<String, Object> m = new LinkedHashMap<>();
    if (r.rejected()) {
      m.put(
          "problems",
          r.problems().stream()
              .map(p -> {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("id", p.id());
                pm.put("line", p.line());
                pm.put("text", p.text());
                return pm;
              })
              .toList());
    } else {
      m.put("entries", r.entries().stream().map(AchJson::entry).toList());
    }
    return m;
  }

  static Map<String, Object> transition(AchModel.Transition t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("entryId", t.entryId());
    m.put("from", t.from() == null ? null : t.from().name());
    m.put("to", t.to().name());
    m.put("kind", t.kind());
    m.put("at", t.at().toString());
    return m;
  }
}
