package io.github.vthattey.twinrail.ui;

import static io.github.vthattey.twinrail.ui.Html.esc;

import java.util.List;

/**
 * Renders the inspector as server-side HTML. The page polls {@code /ui/live} with htmx; the live
 * fragment holds the clock, the timeline, accounts and webhook deliveries. Every value is escaped
 * by {@link Html#esc}; CSS classes come from fixed lists, never from data.
 */
public final class Pages {
  /**
   * The clock steps offered as buttons: label, ISO 8601 duration. The server accepts only these.
   */
  public static final List<String[]> CLOCK_STEPS =
      List.of(
          new String[] {"+30 s", "PT30S"},
          new String[] {"+1 min", "PT1M"},
          new String[] {"+1 hour", "PT1H"},
          new String[] {"+1 day", "P1D"});

  private Pages() {}

  /** The full page. The live fragment is loaded and refreshed by htmx. */
  public static String index() {
    return """
    <!doctype html>
    <html lang="en">
    <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="htmx-config" content='{"allowEval":false,"includeIndicatorStyles":false}'>
    <title>Twinrail inspector</title>
    <link rel="stylesheet" href="/ui/static/ui.css">
    <script src="/ui/static/htmx.min.js" defer></script>
    </head>
    <body>
    <header>
    <h1>Twinrail inspector</h1>
    <p class="note">A local emulator for development and CI. It complements scheme and network testing; it does not replace it.</p>
    </header>
    <main id="live" hx-get="/ui/live" hx-trigger="load, every 2s" hx-swap="innerHTML">
    <p class="empty">Loading&hellip;</p>
    </main>
    </body>
    </html>
    """;
  }

  /** The part of the page that refreshes. */
  public static String live(Snapshot s) {
    StringBuilder out = new StringBuilder(4096);
    out.append("<div class=\"bar\"><span>Virtual time <time>")
        .append(esc(s.now().toString()))
        .append("</time></span><span class=\"steps\">");
    for (String[] step : CLOCK_STEPS) {
      out.append("<button type=\"button\" hx-post=\"/ui/clock/advance\" hx-vals='{\"by\":\"")
          .append(step[1])
          .append("\"}' hx-target=\"#live\" hx-swap=\"innerHTML\">")
          .append(esc(step[0]))
          .append("</button>");
    }
    out.append(
        "</span></div>\n<div class=\"cols\"><section aria-label=\"Timeline\"><h2>Timeline</h2>\n");
    if (s.rows().isEmpty()) {
      out.append(
          "<p class=\"empty\">Nothing yet. Send a pacs.008 to <code>POST /messages</code> or an ACH file to "
              + "<code>POST /ach/files</code>.</p>\n");
    }
    for (Snapshot.Row row : s.rows()) {
      row(out, row);
    }
    out.append("</section>\n<aside><h2>Accounts</h2>\n");
    accounts(out, s.accounts());
    out.append("<h2>Webhook deliveries</h2>\n");
    deliveries(out, s.deliveries());
    out.append("</aside></div>\n");
    return out.toString();
  }

  private static void row(StringBuilder out, Snapshot.Row r) {
    out.append("<details class=\"row ")
        .append(kindClass(r.kind()))
        .append("\" id=\"r-")
        .append(esc(idPart(r.key())))
        .append("\" hx-preserve=\"true\"><summary><time>")
        .append(esc(r.at().toString()))
        .append("</time> <span class=\"kind\">")
        .append(esc(r.kind()))
        .append("</span> <span class=\"title\">")
        .append(esc(r.title()))
        .append("</span>");
    if (r.status() != null) {
      out.append(" <span class=\"chip ")
          .append(statusClass(r.status()))
          .append("\">")
          .append(esc(r.status()))
          .append("</span>");
    }
    out.append("</summary><div class=\"body\">");
    if (r.rule() != null) {
      Snapshot.Rule rule = r.rule();
      out.append("<div class=\"rule\"><strong>Why: rule ")
          .append(esc(rule.id()))
          .append(", reason ")
          .append(esc(rule.reason()))
          .append("</strong><p>")
          .append(esc(rule.text()))
          .append("</p><p class=\"src\">Source: ")
          .append(esc(rule.source()))
          .append("</p></div>");
    }
    if (!r.fields().isEmpty()) {
      out.append("<dl>");
      for (Snapshot.Field f : r.fields()) {
        out.append("<dt>")
            .append(esc(f.label()))
            .append("</dt><dd>")
            .append(esc(f.value()))
            .append("</dd>");
      }
      out.append("</dl>");
    }
    if (r.payload() != null) {
      out.append("<pre>").append(esc(r.payload())).append("</pre>");
    }
    out.append("</div></details>\n");
  }

  private static void accounts(StringBuilder out, List<Snapshot.Account> accounts) {
    if (accounts.isEmpty()) {
      out.append("<p class=\"empty\">No accounts yet.</p>\n");
      return;
    }
    out.append(
        "<table><thead><tr><th>Account</th><th>Balance</th><th>Available</th></tr></thead><tbody>");
    for (Snapshot.Account a : accounts) {
      out.append("<tr><td>")
          .append(esc(a.id()))
          .append("</td><td>")
          .append(esc(a.balance()))
          .append(' ')
          .append(esc(a.currency()))
          .append("</td><td>")
          .append(esc(a.available()))
          .append("</td></tr>");
    }
    out.append("</tbody></table>\n");
  }

  private static void deliveries(StringBuilder out, List<Snapshot.Delivery> deliveries) {
    if (deliveries.isEmpty()) {
      out.append(
          "<p class=\"empty\">None. Register a target with <code>POST /_twinrail/webhooks</code>.</p>\n");
      return;
    }
    out.append(
        "<table><thead><tr><th>Event</th><th>Target</th><th>State</th><th>Tries</th></tr></thead><tbody>");
    for (Snapshot.Delivery d : deliveries) {
      out.append("<tr><td>")
          .append(esc(d.event()))
          .append("</td><td>")
          .append(esc(d.target()))
          .append("</td><td>")
          .append(esc(d.state()))
          .append(d.next() == null ? "" : " (next " + esc(d.next()) + ")")
          .append("</td><td>")
          .append(d.attempts())
          .append("</td></tr>");
    }
    out.append("</tbody></table>\n");
  }

  private static String kindClass(String kind) {
    return switch (kind == null ? "" : kind) {
      case "message" -> "k-message";
      case "payment" -> "k-payment";
      case "ach" -> "k-ach";
      case "clock" -> "k-clock";
      default -> "k-other";
    };
  }

  private static String statusClass(String status) {
    return switch (status) {
      case "ACCEPTED" -> "s-accepted";
      case "SETTLED" -> "s-settled";
      case "REJECTED" -> "s-rejected";
      case "TIMED_OUT" -> "s-timeout";
      case "RETURNED" -> "s-returned";
      default -> "s-other";
    };
  }

  /** Keeps only characters that are safe in an element id. */
  private static String idPart(String key) {
    return key == null ? "x" : key.replaceAll("[^A-Za-z0-9_-]", "_");
  }
}
