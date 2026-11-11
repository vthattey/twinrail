package io.github.vthattey.twinrail.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PagesTest {
  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final String XSS = "<script>alert(1)</script>\"'><img src=x onerror=alert(2)>";

  private static Snapshot.Row row(String key, String kind, String title, String status) {
    return new Snapshot.Row(key, NOW, kind, title, status, List.of(), null, null);
  }

  @Test
  void everyUntrustedFieldIsEscaped() {
    var hostile =
        new Snapshot(
            NOW,
            List.of(new Snapshot.Account(XSS, XSS, XSS, XSS)),
            List.of(
                new Snapshot.Row(
                    XSS,
                    NOW,
                    XSS,
                    XSS,
                    XSS,
                    List.of(new Snapshot.Field(XSS, XSS)),
                    new Snapshot.Rule(XSS, XSS, XSS, XSS),
                    XSS)),
            List.of(new Snapshot.Delivery(XSS, XSS, XSS, XSS, 1, XSS)));
    String html = Pages.live(hostile);

    assertFalse(html.contains("<script>alert"), "no raw script element from data");
    assertFalse(html.contains("<img"), "no raw element from data");
    assertFalse(html.contains("onerror=alert(2)>"), "no unescaped attribute or tag from data");
    assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    assertTrue(html.contains("&quot;&#39;&gt;&lt;img src=x onerror=alert(2)&gt;"));
  }

  @Test
  void aWholeXmlMessageIsShownAsTextNotParsedAsMarkup() {
    String xml = "<?xml version=\"1.0\"?><Document><MsgId>A&B</MsgId></Document>";
    var snap =
        new Snapshot(
            NOW,
            List.of(),
            List.of(
                new Snapshot.Row(
                    "m-1", NOW, "message", "pacs.008 received", null, List.of(), null, xml)),
            List.of());
    String html = Pages.live(snap);
    assertTrue(
        html.contains(
            "<pre>&lt;?xml version=&quot;1.0&quot;?&gt;&lt;Document&gt;&lt;MsgId&gt;A&amp;B&lt;/MsgId&gt;&lt;/Document&gt;</pre>"));
  }

  @Test
  void cssClassesAndIdsNeverComeFromData() {
    var snap =
        new Snapshot(
            NOW,
            List.of(),
            List.of(row("a\" onclick=\"x", "evil\" onclick=\"x", "t", "evil\" onclick=\"x")),
            List.of());
    String html = Pages.live(snap);
    assertTrue(html.contains("class=\"row k-other\""));
    assertTrue(html.contains("chip s-other"));
    assertFalse(
        html.contains("\" onclick=\""),
        "no attribute can be smuggled in through a key, kind or status");
    assertTrue(html.contains("id=\"r-a__onclick__x\""));
  }

  @Test
  void aRejectionShowsTheRuleReasonAndSource() {
    var snap =
        new Snapshot(
            NOW,
            List.of(),
            List.of(
                new Snapshot.Row(
                    "p-1",
                    NOW,
                    "payment",
                    "PMT-000001 rejected",
                    "REJECTED",
                    List.of(),
                    new Snapshot.Rule(
                        "EX-002",
                        "AM01",
                        "The settlement amount must be greater than zero.",
                        "Twinrail example pack 0.1"),
                    null)),
            List.of());
    String html = Pages.live(snap);
    assertTrue(html.contains("Why: rule EX-002, reason AM01"));
    assertTrue(html.contains("The settlement amount must be greater than zero."));
    assertTrue(html.contains("Source: Twinrail example pack 0.1"));
    assertTrue(html.contains("chip s-rejected"));
  }

  @Test
  void rowsAreKeptWhenTheListRefreshes() {
    String html =
        Pages.live(
            new Snapshot(
                NOW, List.of(), List.of(row("p-1", "payment", "x", "SETTLED")), List.of()));
    assertTrue(html.contains("id=\"r-p-1\" hx-preserve=\"true\""));
  }

  @Test
  void emptyStatesSayWhatToDo() {
    String html = Pages.live(new Snapshot(NOW, List.of(), List.of(), List.of()));
    assertTrue(html.contains("POST /messages"));
    assertTrue(html.contains("POST /ach/files"));
    assertTrue(html.contains("POST /_twinrail/webhooks"));
  }

  @Test
  void clockButtonsOfferOnlyTheFixedSteps() {
    String html = Pages.live(new Snapshot(NOW, List.of(), List.of(), List.of()));
    for (String[] step : Pages.CLOCK_STEPS) {
      assertTrue(html.contains("{\"by\":\"" + step[1] + "\"}"));
    }
    assertEquals(4, html.split("hx-post=\"/ui/clock/advance\"", -1).length - 1);
  }

  @Test
  void theIndexPageLoadsOnlyLocalAssetsAndDisablesEval() {
    String html = Pages.index();
    assertTrue(html.contains("/ui/static/htmx.min.js"));
    assertTrue(html.contains("\"allowEval\":false"));
    assertFalse(
        html.contains("http://") || html.contains("https://"),
        "no external resource: the emulator makes no network calls");
    assertTrue(html.contains("does not replace it"));
  }

  @Test
  void onlyListedAssetsCanBeServed() {
    assertTrue(Assets.find("htmx.min.js").isPresent());
    assertTrue(Assets.find("ui.css").isPresent());
    assertTrue(Assets.find("../Pages.class").isEmpty());
    assertTrue(Assets.find("htmx-LICENSE.txt").isEmpty());
    assertTrue(
        new String(Assets.find("htmx.min.js").orElseThrow().bytes()).startsWith("var htmx="));
  }
}
