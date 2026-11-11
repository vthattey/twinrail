package io.github.vthattey.twinrail.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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

class TwinrailServerTest {
  private static final Gson GSON = new Gson();
  private final HttpClient http = HttpClient.newHttpClient();
  private TwinrailServer server;
  private String base;

  @BeforeEach
  void start() throws Exception {
    var pack = RulePackLoader.loadFile(Path.of(System.getProperty("rulePacksDir"), "example.yaml"));
    var config =
        new Emulator.Config(1, Instant.parse("2026-11-09T10:00:00Z"), Duration.ofSeconds(30), pack);
    server = new TwinrailServer(new Emulator(config)).start(0);
    base = "http://localhost:" + server.port();
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    var req =
        HttpRequest.newBuilder(URI.create(base + path))
            .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
            .build();
    return http.send(req, BodyHandlers.ofString());
  }

  private static JsonObject obj(HttpResponse<String> r) {
    return GSON.fromJson(r.body(), JsonObject.class);
  }

  private static String sample() throws Exception {
    try (var in = TwinrailServerTest.class.getResourceAsStream("/pacs008.xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void healthAnswers() throws Exception {
    var r = send("GET", "/_twinrail/health", null);
    assertEquals(200, r.statusCode());
    assertEquals("ok", obj(r).get("status").getAsString());
  }

  @Test
  void demoFlowSendDropJumpClockTimeOut() throws Exception {
    send(
        "PUT",
        "/_twinrail/scenarios",
        "format: 1\nscenarios:\n  - id: hole\n    action: { type: drop }\n");
    var sent = send("POST", "/messages", sample());
    assertEquals(200, sent.statusCode());
    assertEquals("ACCEPTED", obj(sent).get("state").getAsString());
    String id = obj(sent).get("id").getAsString();

    var advanced = send("POST", "/_twinrail/clock/advance", "{\"by\":\"PT30S\"}");
    assertEquals(200, advanced.statusCode());
    assertEquals("2026-11-09T10:00:30Z", obj(advanced).get("now").getAsString());
    assertEquals(1, obj(advanced).getAsJsonArray("transitions").size());

    var after = send("GET", "/_twinrail/payments/" + id, null);
    assertEquals("TIMED_OUT", obj(after).get("state").getAsString());
  }

  @Test
  void rejectionNamesRuleAndReason() throws Exception {
    var r = send("POST", "/messages", sample().replace("125.50<", "0.00<"));
    assertEquals(422, r.statusCode());
    var rejection = obj(r).getAsJsonObject("rejection");
    assertEquals("EX-002", rejection.get("rule").getAsString());
    assertEquals("AM01", rejection.get("reason").getAsString());
    // Key order is part of "same inputs, same bytes": it must not change between JVM runs.
    assertTrue(
        r.body().contains("\"rejection\":{\"rule\":\"EX-002\",\"reason\":\"AM01\",\"text\":\""),
        r.body());
    assertTrue(r.body().indexOf("\"text\"") < r.body().indexOf("\"source\""), r.body());
  }

  @Test
  void accountsAndStatement() throws Exception {
    var created =
        send(
            "POST",
            "/_twinrail/accounts",
            "{\"id\":\"000000001\",\"currency\":\"CAD\",\"balance\":\"500.00\"}");
    assertEquals(201, created.statusCode());
    send("POST", "/messages", sample());

    var acc = send("GET", "/_twinrail/accounts/000000001", null);
    assertEquals("374.50", obj(acc).get("balance").getAsString());

    var statement = send("GET", "/_twinrail/accounts/000000001/statement", null);
    assertEquals(200, statement.statusCode());
    assertTrue(statement.body().contains("camt.053.001.08"));
  }

  @Test
  void resetClearsEverything() throws Exception {
    send("POST", "/messages", sample());
    assertEquals(204, send("POST", "/_twinrail/reset", null).statusCode());
    assertEquals("[]", send("GET", "/_twinrail/payments", null).body());
    assertEquals(
        "2026-11-09T10:00:00Z",
        obj(send("GET", "/_twinrail/clock", null)).get("now").getAsString());
  }

  @Test
  void sessionExportListsTheMessages() throws Exception {
    send("POST", "/messages", sample());
    var r = send("GET", "/_twinrail/session", null);
    assertEquals(200, r.statusCode());
    assertTrue(r.body().contains("pacs.008.001.08"));
  }

  @Test
  void badInputGetsA400WithAnActionableMessage() throws Exception {
    var notXml = send("POST", "/messages", "hello");
    assertEquals(400, notXml.statusCode());
    assertTrue(obj(notXml).get("error").getAsString().contains("Check"));

    var badClock = send("POST", "/_twinrail/clock/advance", "{}");
    assertEquals(400, badClock.statusCode());
    assertTrue(obj(badClock).get("error").getAsString().contains("by"));

    var backwards = send("POST", "/_twinrail/clock/set", "{\"to\":\"2020-01-01T00:00:00Z\"}");
    assertEquals(400, backwards.statusCode());

    var badScenario =
        send(
            "PUT",
            "/_twinrail/scenarios",
            "format: 1\nscenarios:\n  - id: x\n    action: { type: nope }\n");
    assertEquals(400, badScenario.statusCode());

    assertEquals(404, send("GET", "/_twinrail/accounts/none", null).statusCode());
    assertEquals(404, send("GET", "/_twinrail/payments/none", null).statusCode());
  }

  @Test
  void twoRunsWithTheSameInputsGiveTheSameResponses() throws Exception {
    String first = send("POST", "/messages", sample()).body();
    send("POST", "/_twinrail/reset", null);
    assertEquals(first, send("POST", "/messages", sample()).body());
  }
}
