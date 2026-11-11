package io.github.vthattey.twinrail.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.rules.RulePackLoader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UiServerTest {
  private final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  private TwinrailServer server;
  private String base;

  @BeforeEach
  void start() {
    var pack = RulePackLoader.loadFile(Path.of(System.getProperty("rulePacksDir"), "example.yaml"));
    var config =
        new Emulator.Config(1, Instant.parse("2026-11-10T10:00:00Z"), Duration.ofSeconds(30), pack);
    server = new TwinrailServer(new Emulator(config), (t, h, b) -> 200).start(0);
    base = "http://localhost:" + server.port();
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(base + path)).build(), BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String contentType, String body, String... headers)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", contentType)
            .POST(BodyPublishers.ofString(body));
    for (int i = 0; i < headers.length; i += 2) {
      b.header(headers[i], headers[i + 1]);
    }
    return http.send(b.build(), BodyHandlers.ofString());
  }

  private static String sample() throws Exception {
    try (var in = UiServerTest.class.getResourceAsStream("/pacs008.xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void pageAndAssetsAreServedWithStrictHeaders() throws Exception {
    var page = get("/ui");
    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains("Twinrail inspector"));
    String csp = page.headers().firstValue("Content-Security-Policy").orElseThrow();
    assertTrue(csp.contains("default-src 'none'") && csp.contains("script-src 'self'"));
    assertEquals("nosniff", page.headers().firstValue("X-Content-Type-Options").orElseThrow());

    var js = get("/ui/static/htmx.min.js");
    assertEquals(200, js.statusCode());
    assertTrue(js.headers().firstValue("Content-Type").orElseThrow().startsWith("text/javascript"));
    assertTrue(js.body().startsWith("var htmx="));
    assertEquals(200, get("/ui/static/ui.css").statusCode());
    assertEquals(404, get("/ui/static/nope.js").statusCode());
    assertEquals(404, get("/ui/static/..%2FPages.class").statusCode());

    var root = get("/");
    assertEquals(302, root.statusCode());
    assertEquals("/ui", root.headers().firstValue("Location").orElseThrow());
  }

  @Test
  void theTimelineShowsMessageThenStateChangesNewestFirst() throws Exception {
    post("/messages", "application/xml", sample());
    String html = get("/ui/live").body();

    int settled = html.indexOf("PMT-000001 settled");
    int accepted = html.indexOf("PMT-000001 accepted");
    int message = html.indexOf("pacs.008.001.08 inbound");
    assertTrue(settled > 0 && accepted > settled && message > accepted, html);
    assertTrue(html.contains("2026-11-10T10:00:00Z"));
    assertTrue(html.contains("000000001") && html.contains("125.50 CAD"));
  }

  @Test
  void aRejectionShowsTheRuleBehindIt() throws Exception {
    post("/messages", "application/xml", sample().replace("125.50<", "0.00<"));
    String html = get("/ui/live").body();
    assertTrue(html.contains("PMT-000001 rejected"));
    assertTrue(html.contains("Why: rule EX-002, reason AM01"));
    assertTrue(html.contains("The settlement amount must be greater than zero."));
    assertTrue(html.contains("Source: Twinrail example pack 0.1"));
  }

  @Test
  void hostileMessageContentIsEscapedEndToEnd() throws Exception {
    String hostile = "&lt;img src=x onerror=alert(1)&gt;"; // XML text that decodes to a tag
    post("/messages", "application/xml", sample().replace("E2E-1", hostile));
    String html = get("/ui/live").body();

    assertFalse(html.contains("<img src=x"), "decoded text must not become markup");
    assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
    // The raw XML is shown as text too, with its own entities escaped once more.
    assertTrue(html.contains("&amp;lt;img src=x"));
  }

  @Test
  void anAchReturnShowsTheCodeTimeFrameAndSource() throws Exception {
    var put =
        http.send(
            HttpRequest.newBuilder(URI.create(base + "/_twinrail/scenarios"))
                .PUT(
                    BodyPublishers.ofString(
                        "format: 1\nscenarios:\n  - id: r\n    action: { type: return, code: R02 }\n"))
                .build(),
            BodyHandlers.ofString());
    assertEquals(200, put.statusCode());
    post("/ach/files", "text/plain", AchTestFiles.credits());

    String html = get("/ui/live").body();
    assertTrue(html.contains("ACH-000001 returned R02"));
    assertTrue(html.contains("Why: rule ach.return, reason R02"));
    assertTrue(html.contains("Account Closed"));
    assertTrue(html.contains("2 Banking Days"));
    assertTrue(html.contains("Modern Treasury"));
  }

  @Test
  void clockButtonsMoveTheClockAndReturnTheFreshTimeline() throws Exception {
    var form = "application/x-www-form-urlencoded";
    assertEquals(
        403, post("/ui/clock/advance", form, "by=PT30S").statusCode(), "no HX-Request header");
    assertEquals(
        400, post("/ui/clock/advance", form, "by=PT99H", "HX-Request", "true").statusCode());
    assertEquals(400, post("/ui/clock/advance", form, "", "HX-Request", "true").statusCode());
    assertTrue(
        get("/_twinrail/clock").body().contains("2026-11-10T10:00:00Z"),
        "refused calls changed nothing");

    var ok = post("/ui/clock/advance", form, "by=PT30S", "HX-Request", "true");
    assertEquals(200, ok.statusCode());
    assertTrue(ok.body().contains("Clock advanced by PT30S"));
    assertTrue(ok.body().contains("2026-11-10T10:00:30Z"));
    assertTrue(get("/_twinrail/clock").body().contains("2026-11-10T10:00:30Z"));
  }

  @Test
  void aDroppedPaymentShowsItsTimeOutAfterTheClockJump() throws Exception {
    var put =
        http.send(
            HttpRequest.newBuilder(URI.create(base + "/_twinrail/scenarios"))
                .PUT(
                    BodyPublishers.ofString(
                        "format: 1\nscenarios:\n  - id: hole\n    action: { type: drop }\n"))
                .build(),
            BodyHandlers.ofString());
    assertEquals(200, put.statusCode());
    post("/messages", "application/xml", sample());
    post("/ui/clock/advance", "application/x-www-form-urlencoded", "by=PT1M", "HX-Request", "true");

    String html = get("/ui/live").body();
    assertTrue(html.contains("PMT-000001 timed out"));
    assertTrue(
        html.contains("2026-11-10T10:00:30Z"), "the time-out is shown at the time it was due");
    assertTrue(html.contains("chip s-timeout"));
  }
}
