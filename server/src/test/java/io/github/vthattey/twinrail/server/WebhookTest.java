package io.github.vthattey.twinrail.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import io.github.vthattey.twinrail.core.rules.RulePackLoader;
import java.net.InetSocketAddress;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookTest {
  private static final Gson GSON = new Gson();
  private static final String HOOK = "{\"url\":\"http://localhost:9/hook\",\"secret\":\"s3cret\"}";

  /** One captured webhook call. */
  record Call(URI target, Map<String, String> headers, String body) {}

  private final HttpClient http = HttpClient.newHttpClient();
  private final List<Call> calls = new ArrayList<>();
  private final List<Integer> statuses = new ArrayList<>(); // scripted answers, then 200
  private TwinrailServer server;
  private String base;

  @BeforeEach
  void start() {
    var pack = RulePackLoader.loadFile(Path.of(System.getProperty("rulePacksDir"), "example.yaml"));
    var config =
        new Emulator.Config(1, Instant.parse("2026-11-09T10:00:00Z"), Duration.ofSeconds(30), pack);
    WebhookSender fake =
        (target, headers, body) -> {
          calls.add(new Call(target, headers, body));
          return statuses.isEmpty() ? 200 : statuses.remove(0);
        };
    server = new TwinrailServer(new Emulator(config), fake).start(0);
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

  private static String sample() throws Exception {
    try (var in = WebhookTest.class.getResourceAsStream("/pacs008.xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static JsonObject obj(String json) {
    return GSON.fromJson(json, JsonObject.class);
  }

  @Test
  void settledPaymentSendsAcceptedThenSettledEventsSigned() throws Exception {
    assertEquals(201, send("POST", "/_twinrail/webhooks", HOOK).statusCode());
    send("POST", "/messages", sample());

    assertEquals(2, calls.size());
    JsonObject first = obj(calls.get(0).body());
    JsonObject second = obj(calls.get(1).body());
    assertEquals("payment.accepted", first.get("type").getAsString());
    assertEquals("payment.settled", second.get("type").getAsString());
    assertEquals("EVT-000001", first.get("id").getAsString());
    assertEquals("2026-11-09T10:00:00Z", second.get("time").getAsString());
    assertEquals("SETTLED", second.getAsJsonObject("payload").get("state").getAsString());

    Call c = calls.get(1);
    assertEquals(
        "sha256=" + Webhooks.sign("s3cret", c.body()), c.headers().get("Twinrail-Signature"));
    assertEquals("EVT-000002", c.headers().get("Twinrail-Event-Id"));
  }

  @Test
  void signatureMatchesAKnownHmacSha256Vector() {
    // RFC 4231 style check with an independently computed value.
    assertEquals(
        "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
        Webhooks.sign("key", "The quick brown fox jumps over the lazy dog"));
  }

  @Test
  void rejectedPaymentSendsRejectedEventWithTheRule() throws Exception {
    send("POST", "/_twinrail/webhooks", HOOK);
    send("POST", "/messages", sample().replace("125.50<", "0.00<"));
    assertEquals(1, calls.size());
    JsonObject e = obj(calls.get(0).body());
    assertEquals("payment.rejected", e.get("type").getAsString());
    assertEquals(
        "EX-002",
        e.getAsJsonObject("payload").getAsJsonObject("rejection").get("rule").getAsString());
  }

  @Test
  void failedDeliveryRetriesOnVirtualTimeWithBackoff() throws Exception {
    statuses.addAll(List.of(500, -1)); // first event: attempt 1 fails, attempt 2 unreachable
    send("POST", "/_twinrail/webhooks", HOOK);
    send(
        "PUT",
        "/_twinrail/scenarios",
        "format: 1\nscenarios:\n  - id: hole\n    action: { type: drop }\n");
    send("POST", "/messages", sample()); // one event: payment.accepted
    assertEquals(1, calls.size());

    // No real waiting: nothing happens until the virtual clock moves.
    send("POST", "/_twinrail/clock/advance", "{\"by\":\"PT0S\"}");
    assertEquals(1, calls.size());

    send("POST", "/_twinrail/clock/advance", "{\"by\":\"PT1S\"}"); // retry 1 due at +1s
    assertEquals(2, calls.size());
    send("POST", "/_twinrail/clock/advance", "{\"by\":\"PT1S\"}"); // retry 2 due at +2s: not yet
    assertEquals(2, calls.size());
    send("POST", "/_twinrail/clock/advance", "{\"by\":\"PT1S\"}"); // +2s after attempt 2
    assertEquals(3, calls.size());

    JsonArray deliveries =
        GSON.fromJson(send("GET", "/_twinrail/webhooks/deliveries", null).body(), JsonArray.class);
    assertEquals("DELIVERED", deliveries.get(0).getAsJsonObject().get("state").getAsString());
    assertEquals(3, deliveries.get(0).getAsJsonObject().get("attempts").getAsInt());
  }

  @Test
  void givesUpAfterFiveAttempts() throws Exception {
    for (int i = 0; i < 10; i++) {
      statuses.add(503);
    }
    send("POST", "/_twinrail/webhooks", HOOK);
    send(
        "PUT",
        "/_twinrail/scenarios",
        "format: 1\nscenarios:\n  - id: hole\n    action: { type: drop }\n");
    send("POST", "/messages", sample());
    for (int i = 0; i < 4; i++) {
      send("POST", "/_twinrail/clock/advance", "{\"by\":\"PT8S\"}");
    }
    assertEquals(5, calls.stream().filter(c -> c.body().contains("payment.accepted")).count());
    JsonArray deliveries =
        GSON.fromJson(send("GET", "/_twinrail/webhooks/deliveries", null).body(), JsonArray.class);
    assertEquals("FAILED", deliveries.get(0).getAsJsonObject().get("state").getAsString());
  }

  @Test
  void nonLocalTargetsAreRefusedWithAnExplanation() throws Exception {
    for (String url :
        List.of("http://example.com/hook", "http://8.8.8.8/hook", "http://api.partner.io/x")) {
      var r = send("POST", "/_twinrail/webhooks", "{\"url\":\"" + url + "\",\"secret\":\"x\"}");
      assertEquals(400, r.statusCode(), url);
      assertTrue(obj(r.body()).get("error").getAsString().contains("not local"), url);
    }
    for (String url :
        List.of(
            "http://localhost:9000/h",
            "http://127.0.0.1:9000/h",
            "http://192.168.1.20/h",
            "http://10.0.0.5/h",
            "http://[::1]:9000/h",
            "http://mockbank:8080/h",
            "http://host.docker.internal:9000/h")) {
      var r = send("POST", "/_twinrail/webhooks", "{\"url\":\"" + url + "\",\"secret\":\"x\"}");
      assertEquals(201, r.statusCode(), url);
    }
  }

  @Test
  void secretIsRequiredAndNeverListed() throws Exception {
    var missing = send("POST", "/_twinrail/webhooks", "{\"url\":\"http://localhost:9/h\"}");
    assertEquals(400, missing.statusCode());
    send("POST", "/_twinrail/webhooks", HOOK);
    assertTrue(!send("GET", "/_twinrail/webhooks", null).body().contains("s3cret"));
  }

  @Test
  void removedWebhookReceivesNothing() throws Exception {
    send("POST", "/_twinrail/webhooks", HOOK);
    assertEquals(204, send("DELETE", "/_twinrail/webhooks/WH-001", null).statusCode());
    assertEquals(404, send("DELETE", "/_twinrail/webhooks/WH-001", null).statusCode());
    send("POST", "/messages", sample());
    assertTrue(calls.isEmpty());
  }

  @Test
  void resetClearsTargetsAndDeliveries() throws Exception {
    send("POST", "/_twinrail/webhooks", HOOK);
    send("POST", "/_twinrail/reset", null);
    assertEquals("[]", send("GET", "/_twinrail/webhooks", null).body());
    assertEquals("[]", send("GET", "/_twinrail/webhooks/deliveries", null).body());
  }

  @Test
  void realHttpDeliveryReachesALocalListener() throws Exception {
    var received = new CopyOnWriteArrayList<String>();
    HttpServer listener = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    listener.createContext(
        "/hook",
        ex -> {
          received.add(
              ex.getRequestHeaders().getFirst("Twinrail-Signature")
                  + "|"
                  + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          ex.sendResponseHeaders(204, -1);
          ex.close();
        });
    listener.start();
    try {
      var pack =
          RulePackLoader.loadFile(Path.of(System.getProperty("rulePacksDir"), "example.yaml"));
      var config =
          new Emulator.Config(
              1, Instant.parse("2026-11-09T10:00:00Z"), Duration.ofSeconds(30), pack);
      var real = new TwinrailServer(new Emulator(config)).start(0);
      try {
        String realBase = "http://localhost:" + real.port();
        var hook =
            "{\"url\":\"http://127.0.0.1:"
                + listener.getAddress().getPort()
                + "/hook\",\"secret\":\"k\"}";
        http.send(
            HttpRequest.newBuilder(URI.create(realBase + "/_twinrail/webhooks"))
                .POST(BodyPublishers.ofString(hook))
                .build(),
            BodyHandlers.ofString());
        http.send(
            HttpRequest.newBuilder(URI.create(realBase + "/messages"))
                .POST(BodyPublishers.ofString(sample()))
                .build(),
            BodyHandlers.ofString());
      } finally {
        real.stop();
      }
    } finally {
      listener.stop(0);
    }
    assertEquals(2, received.size());
    String[] parts = received.get(1).split("\\|", 2);
    assertEquals("sha256=" + Webhooks.sign("k", parts[1]), parts[0]);
  }
}
