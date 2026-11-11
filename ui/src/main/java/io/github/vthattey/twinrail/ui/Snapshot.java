package io.github.vthattey.twinrail.ui;

import java.time.Instant;
import java.util.List;

/**
 * Everything the inspector shows at one moment, as plain data. The server builds it; this module
 * only renders it. All strings are treated as untrusted.
 *
 * @param now the virtual time
 * @param rows the timeline, newest first
 */
public record Snapshot(
    Instant now, List<Account> accounts, List<Row> rows, List<Delivery> deliveries) {
  public Snapshot {
    accounts = List.copyOf(accounts);
    rows = List.copyOf(rows);
    deliveries = List.copyOf(deliveries);
  }

  /** An account balance. */
  public record Account(String id, String currency, String balance, String available) {}

  /**
   * One line of the timeline. Rows are history: once a row exists it never changes, which is why
   * the page can keep a row the reader has expanded while the list around it refreshes.
   *
   * @param key a stable identifier for the row, unique in the timeline
   * @param kind {@code message}, {@code payment}, {@code ach} or {@code clock}; anything else is
   *     shown as plain
   * @param status a state such as {@code SETTLED}; may be {@code null}
   * @param fields label and value pairs shown when the row is expanded
   * @param rule the rule behind a rejection or return, or {@code null}
   * @param payload a whole message or file to show verbatim when expanded, or {@code null}
   */
  public record Row(
      String key,
      Instant at,
      String kind,
      String title,
      String status,
      List<Field> fields,
      Rule rule,
      String payload) {
    public Row {
      fields = List.copyOf(fields);
    }
  }

  public record Field(String label, String value) {}

  /** The rule behind a rejection or return: a rejection always names the rule and reason. */
  public record Rule(String id, String reason, String text, String source) {}

  /** A webhook delivery and how it is going. */
  public record Delivery(
      String id, String target, String event, String state, int attempts, String next) {}
}
