package io.github.vthattey.twinrail.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.vthattey.twinrail.core.rules.RulePackLoader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AchServerTest {
  private static final Gson GSON = new Gson();
  private static final String SCENARIOS =
      "format: 1\nscenarios:\n"
          + "  - id: closed\n    match: { message: nacha.entry, where: [{ path: /Entry/IndividualName, equals: \"JANE DOE\" }] }\n"
          + "    action: { type: return, code: R02, after: PT1H, text: Account closed }\n"
          + "  - id: moved\n    match: { message: nacha.entry, where: [{ path: /Entry/IndividualName, equals: \"JOHN ROE\" }] }\n"
          + "    action: { type: noc, code: C01, account: \"55555555\" }\n";

  private final HttpClient http = HttpClient.newHttpClient();
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

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    var req =
        HttpRequest.newBuilder(URI.create(base + path))
            .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
            .build();
    return http.send(req, BodyHandlers.ofString());
  }

  private static JsonObject obj(String json) {
    return GSON.fromJson(json, JsonObject.class);
  }

  @Test
  void returnAfterAnHourAndAChangeNotificationOverHttp() throws Exception {
    assertEquals(200, send("PUT", "/_twinrail/scenarios", SCENARIOS).statusCode());
    var received = send("POST", "/ach/files", AchTestFiles.credits());
    assertEquals(200, received.statusCode());
    JsonArray entries = obj(received.body()).getAsJsonArray("entries");
    assertEquals("SETTLED", entries.get(0).getAsJsonObject().get("state").getAsString());

    // JOHN ROE's entry carries a change notification and stays settled.
    var change = entries.get(1).getAsJsonObject().getAsJsonObject("change");
    assertEquals("C01", change.get("code").getAsString());
    assertEquals(94, change.get("addendaRecord").getAsString().length());
    assertEquals("SETTLED", entries.get(1).getAsJsonObject().get("state").getAsString());

    // Nothing happens to JANE DOE's entry until the clock moves an hour.
    var early = send("POST", "/_twinrail/clock/advance", "{\"by\":\"PT59M\"}");
    assertFalse(obj(early.body()).has("achTransitions"));

    var due = send("POST", "/_twinrail/clock/advance", "{\"by\":\"PT1M\"}");
    var transitions = obj(due.body()).getAsJsonArray("achTransitions");
    assertEquals(1, transitions.size());
    assertEquals("returned", transitions.get(0).getAsJsonObject().get("kind").getAsString());

    var returned = obj(send("GET", "/_twinrail/ach/entries/ACH-000001", null).body());
    assertEquals("RETURNED", returned.get("state").getAsString());
    var ret = returned.getAsJsonObject("return");
    assertEquals("R02", ret.get("code").getAsString());
    assertEquals("Account Closed", ret.get("title").getAsString());
    assertEquals("2 Banking Days", ret.get("timeframe").getAsString());
    assertEquals("2026-11-10T11:00:00Z", ret.get("at").getAsString());

    // The money was reversed: ACC-1 is back to zero, ACC-2 kept its credit.
    assertEquals(
        "0.00",
        obj(send("GET", "/_twinrail/accounts/ACC-1", null).body()).get("balance").getAsString());
    assertEquals(
        "2125.50",
        obj(send("GET", "/_twinrail/accounts/ACC-2", null).body()).get("balance").getAsString());
  }

  @Test
  void aFileWithWrongTotalsIsRefusedWithProblemIds() throws Exception {
    var r =
        send("POST", "/ach/files", AchTestFiles.credits().replace("000000362550", "000000362551"));
    assertEquals(422, r.statusCode());
    var problems = obj(r.body()).getAsJsonArray("problems");
    assertTrue(problems.toString().contains("nacha.batch.total-credit"));
    assertEquals("[]", send("GET", "/_twinrail/ach/entries", null).body());
  }

  @Test
  void aScenarioWithAnUnknownReturnCodeIsRefusedAndChangesNothing() throws Exception {
    send("PUT", "/_twinrail/scenarios", SCENARIOS);
    var bad =
        send(
            "PUT",
            "/_twinrail/scenarios",
            "format: 1\nscenarios:\n  - id: x\n    action: { type: return, code: R99 }\n");
    assertEquals(400, bad.statusCode());
    assertTrue(obj(bad.body()).get("error").getAsString().contains("R99"));
    // The earlier scenarios are still in force.
    assertEquals(
        2, GSON.fromJson(send("GET", "/_twinrail/scenarios", null).body(), JsonArray.class).size());
  }

  @Test
  void resetClearsAchEntries() throws Exception {
    send("POST", "/ach/files", AchTestFiles.credits());
    send("POST", "/_twinrail/reset", null);
    assertEquals("[]", send("GET", "/_twinrail/ach/entries", null).body());
  }

  @Test
  void aFileThatIsNotAnAchFileGetsAnActionable400() throws Exception {
    var r = send("POST", "/ach/files", "hello");
    assertEquals(400, r.statusCode());
    assertTrue(obj(r.body()).get("error").getAsString().contains("94"));
  }
}
