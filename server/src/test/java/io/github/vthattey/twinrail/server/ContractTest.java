package io.github.vthattey.twinrail.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.Gson;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The OpenAPI file and the server must agree (ADR 0005): the same operations exist, every request
 * body the tests send is valid against the file, every response uses a documented status and
 * validates against its schema, and webhook bodies match the documented event.
 */
class ContractTest {
  private static final Gson GSON = new Gson();
  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
  private static final List<String> METHODS = List.of("get", "post", "put", "delete");

  private final HttpClient http = HttpClient.newHttpClient();
  private final List<String> webhookBodies = new ArrayList<>();
  private final Set<String> exercised = new HashSet<>();
  private boolean invalidRequest; // set by callBad: the request is wrong on purpose
  private Map<String, Object> spec;
  private TwinrailServer server;
  private String base;

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object o) {
    return (Map<String, Object>) o;
  }

  @BeforeEach
  void start() throws Exception {
    spec =
        map(
            new Load(LoadSettings.builder().build())
                .loadFromString(
                    new String(
                        getClass().getResourceAsStream("/openapi/control-1.yaml").readAllBytes(),
                        StandardCharsets.UTF_8)));
    var pack = RulePackLoader.loadFile(Path.of(System.getProperty("rulePacksDir"), "example.yaml"));
    var config =
        new Emulator.Config(1, Instant.parse("2026-11-09T10:00:00Z"), Duration.ofSeconds(30), pack);
    server =
        new TwinrailServer(
                new Emulator(config),
                (target, headers, body) -> {
                  webhookBodies.add(body);
                  return 200;
                })
            .start(0);
    base = "http://localhost:" + server.port();
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  // --- helpers ---------------------------------------------------------------------------

  private Schema schemaFor(Object node) {
    Map<String, Object> root = new HashMap<>(map(node));
    root.put("components", spec.get("components"));
    return REGISTRY.getSchema(GSON.toJson(root));
  }

  private void assertValid(Object schemaNode, String json, String what) {
    var errors = schemaFor(schemaNode).validate(json, InputFormat.JSON);
    assertTrue(
        errors.isEmpty(), what + " does not match the OpenAPI schema: " + errors + "\n" + json);
  }

  private Map<String, Object> operation(String method, String template) {
    var item = map(map(spec.get("paths")).get(template));
    if (item == null || item.get(method.toLowerCase()) == null) {
      fail("The OpenAPI file has no " + method + " " + template);
    }
    return map(item.get(method.toLowerCase()));
  }

  /** Resolves a $ref such as #/components/responses/BadRequest. */
  private Map<String, Object> resolve(Map<String, Object> node) {
    Object ref = node.get("$ref");
    if (ref == null) {
      return node;
    }
    Object cur = spec;
    for (String part : ref.toString().substring(2).split("/")) {
      cur = map(cur).get(part);
    }
    return map(cur);
  }

  private HttpResponse<String> call(
      String method, String template, String actual, String contentType, String body)
      throws Exception {
    var op = operation(method, template);
    if (!invalidRequest && body != null && contentType.equals("application/json")) {
      var content = map(map(op.get("requestBody")).get("content"));
      assertValid(
          map(content.get("application/json")).get("schema"),
          body,
          method + " " + template + " request");
    }
    var builder = HttpRequest.newBuilder(URI.create(base + actual));
    if (contentType != null) {
      builder.header("Content-Type", contentType);
    }
    var response =
        http.send(
            builder
                .method(
                    method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
                .build(),
            BodyHandlers.ofString());

    String status = String.valueOf(response.statusCode());
    var responses = map(op.get("responses"));
    assertTrue(
        responses.containsKey(status),
        method
            + " "
            + template
            + " answered "
            + status
            + " but the file documents "
            + responses.keySet());
    exercised.add(method + " " + template);

    var content = resolve(map(responses.get(status))).get("content");
    if (content != null) {
      var json = map(content).get("application/json");
      if (json != null) {
        assertValid(
            map(json).get("schema"), response.body(), method + " " + template + " -> " + status);
      }
    }
    return response;
  }

  /** Like call, for a request that breaks the schema on purpose to provoke a 400. */
  private HttpResponse<String> callBad(
      String method, String template, String actual, String contentType, String body)
      throws Exception {
    invalidRequest = true;
    try {
      return call(method, template, actual, contentType, body);
    } finally {
      invalidRequest = false;
    }
  }

  private static String sample(String amount) throws Exception {
    try (var in = ContractTest.class.getResourceAsStream("/pacs008.xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("125.50<", amount + "<");
    }
  }

  // --- tests -----------------------------------------------------------------------------

  @Test
  void documentedAndRegisteredOperationsAreTheSame() {
    Set<String> documented = new TreeSet<>();
    for (var path : map(spec.get("paths")).entrySet()) {
      for (String method : METHODS) {
        if (map(path.getValue()).containsKey(method)) {
          documented.add(method.toUpperCase() + " " + path.getKey());
        }
      }
    }
    assertEquals(documented, new TreeSet<>(server.routes()));
  }

  @Test
  void everyOperationAnswersAsDocumented() throws Exception {
    String json = "application/json";
    call("GET", "/_twinrail/health", "/_twinrail/health", null, null);
    call("GET", "/_twinrail/openapi.yaml", "/_twinrail/openapi.yaml", null, null);
    call("GET", "/_twinrail/clock", "/_twinrail/clock", null, null);
    call(
        "POST",
        "/_twinrail/accounts",
        "/_twinrail/accounts",
        json,
        "{\"id\":\"000000001\",\"currency\":\"CAD\",\"balance\":\"500.00\"}");
    callBad("POST", "/_twinrail/accounts", "/_twinrail/accounts", json, "{\"id\":\"x\"}"); // 400
    call("GET", "/_twinrail/accounts", "/_twinrail/accounts", null, null);
    call("GET", "/_twinrail/accounts/{id}", "/_twinrail/accounts/000000001", null, null);
    call("GET", "/_twinrail/accounts/{id}", "/_twinrail/accounts/none", null, null); // 404

    call(
        "POST",
        "/_twinrail/webhooks",
        "/_twinrail/webhooks",
        json,
        "{\"url\":\"http://localhost:9/hook\",\"secret\":\"s\"}");
    call(
        "POST",
        "/_twinrail/webhooks",
        "/_twinrail/webhooks",
        json,
        "{\"url\":\"http://example.com/hook\",\"secret\":\"s\"}"); // 400
    call("GET", "/_twinrail/webhooks", "/_twinrail/webhooks", null, null);

    call(
        "PUT",
        "/_twinrail/scenarios",
        "/_twinrail/scenarios",
        "application/yaml",
        "format: 1\nscenarios:\n  - id: slow\n    action: { type: delay, duration: PT10S }\n");
    call("PUT", "/_twinrail/scenarios", "/_twinrail/scenarios", "application/yaml", "nonsense: [");
    call("GET", "/_twinrail/scenarios", "/_twinrail/scenarios", null, null);

    var accepted = call("POST", "/messages", "/messages", "application/xml", sample("125.50"));
    assertEquals(200, accepted.statusCode());
    call("POST", "/messages", "/messages", "application/xml", sample("0.00")); // 422
    call("POST", "/messages", "/messages", "application/xml", "hello"); // 400

    // ACH: a return after an hour for JANE DOE, a change notification for JOHN ROE.
    call(
        "PUT",
        "/_twinrail/scenarios",
        "/_twinrail/scenarios",
        "application/yaml",
        "format: 1\nscenarios:\n"
            + "  - id: closed\n    match: { message: nacha.entry, where: [{ path: /Entry/IndividualName, equals: \"JANE DOE\" }] }\n"
            + "    action: { type: return, code: R02, after: PT1H }\n"
            + "  - id: moved\n    match: { message: nacha.entry, where: [{ path: /Entry/IndividualName, equals: \"JOHN ROE\" }] }\n"
            + "    action: { type: noc, code: C01, account: \"55555555\" }\n");
    var ach = call("POST", "/ach/files", "/ach/files", "text/plain", AchTestFiles.credits());
    assertEquals(200, ach.statusCode());
    String achId =
        GSON.fromJson(ach.body(), com.google.gson.JsonObject.class)
            .getAsJsonArray("entries")
            .get(0)
            .getAsJsonObject()
            .get("id")
            .getAsString();
    call(
        "POST",
        "/ach/files",
        "/ach/files",
        "text/plain",
        AchTestFiles.credits().replace("000000362550", "000000362551")); // 422
    call("POST", "/ach/files", "/ach/files", "text/plain", "garbage"); // 400
    call("GET", "/_twinrail/ach/entries", "/_twinrail/ach/entries", null, null);
    call("GET", "/_twinrail/ach/entries/{id}", "/_twinrail/ach/entries/" + achId, null, null);
    call("GET", "/_twinrail/ach/entries/{id}", "/_twinrail/ach/entries/none", null, null); // 404

    call("GET", "/_twinrail/payments", "/_twinrail/payments", null, null);
    call("GET", "/_twinrail/payments/{id}", "/_twinrail/payments/PMT-000001", null, null);
    call("GET", "/_twinrail/payments/{id}", "/_twinrail/payments/none", null, null);

    call(
        "POST", "/_twinrail/clock/advance", "/_twinrail/clock/advance", json, "{\"by\":\"PT10S\"}");
    callBad("POST", "/_twinrail/clock/advance", "/_twinrail/clock/advance", json, "{}"); // 400
    call(
        "POST",
        "/_twinrail/clock/set",
        "/_twinrail/clock/set",
        json,
        "{\"to\":\"2026-11-09T11:00:00Z\"}");
    call(
        "POST",
        "/_twinrail/clock/set",
        "/_twinrail/clock/set",
        json,
        "{\"to\":\"2020-01-01T00:00:00Z\"}"); // 400

    call(
        "GET",
        "/_twinrail/accounts/{id}/statement",
        "/_twinrail/accounts/000000001/statement",
        null,
        null);
    call(
        "GET",
        "/_twinrail/accounts/{id}/statement",
        "/_twinrail/accounts/none/statement",
        null,
        null);
    call("GET", "/_twinrail/webhooks/deliveries", "/_twinrail/webhooks/deliveries", null, null);
    call("GET", "/_twinrail/session", "/_twinrail/session", null, null);
    call("DELETE", "/_twinrail/webhooks/{id}", "/_twinrail/webhooks/WH-001", null, null);
    call("DELETE", "/_twinrail/webhooks/{id}", "/_twinrail/webhooks/WH-001", null, null); // 404
    call("POST", "/_twinrail/reset", "/_twinrail/reset", null, null);

    Set<String> all = new TreeSet<>();
    for (var path : map(spec.get("paths")).entrySet()) {
      for (String method : METHODS) {
        if (map(path.getValue()).containsKey(method)) {
          all.add(method.toUpperCase() + " " + path.getKey());
        }
      }
    }
    all.removeAll(exercised);
    assertTrue(all.isEmpty(), "Operations in the file that the contract test never called: " + all);
  }

  @Test
  void webhookBodiesMatchTheDocumentedEvent() throws Exception {
    call(
        "POST",
        "/_twinrail/webhooks",
        "/_twinrail/webhooks",
        "application/json",
        "{\"url\":\"http://localhost:9/hook\",\"secret\":\"s\"}");
    call("POST", "/messages", "/messages", "application/xml", sample("5.00"));
    call("POST", "/messages", "/messages", "application/xml", sample("0.00"));
    assertEquals(3, webhookBodies.size()); // accepted + settled, rejected

    var event = map(map(spec.get("components")).get("schemas")).get("WebhookEvent");
    for (String body : webhookBodies) {
      assertValid(event, body, "webhook body");
    }
  }
}
