package io.github.vthattey.twinrail.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.vthattey.twinrail.core.codec.CodecException;
import io.github.vthattey.twinrail.core.definition.DefinitionException;
import io.github.vthattey.twinrail.core.ledger.LedgerException;
import io.github.vthattey.twinrail.core.lifecycle.Payment;
import io.github.vthattey.twinrail.core.lifecycle.Transition;
import io.github.vthattey.twinrail.core.recorder.SessionFiles;
import io.github.vthattey.twinrail.core.scenario.ScenarioLoader;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The HTTP face of the emulator: {@code POST /messages} for payment messages and {@code
 * /_twinrail/...} for the control API. Responses are JSON except statements, which are ISO 20022
 * XML. Every error body says what went wrong and what to do next.
 */
public final class TwinrailServer {
  private static final String API = "/_twinrail";
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private final Emulator emulator;
  private final Javalin app;
  private final List<String> routes = new java.util.ArrayList<>();
  private final WebhookSender sender;

  public TwinrailServer(Emulator emulator) {
    this(emulator, WebhookSender.http());
  }

  /**
   * @param sender how webhooks are posted; tests pass a fake
   */
  public TwinrailServer(Emulator emulator, WebhookSender sender) {
    this.emulator = emulator;
    this.sender = sender;
    this.app = Javalin.create(config -> config.showJavalinBanner = false);
    registerRoutes();
    errors();
  }

  public TwinrailServer start(int port) {
    app.start(port);
    return this;
  }

  public int port() {
    return app.port();
  }

  public void stop() {
    app.stop();
  }

  private void route(String method, String path, io.javalin.http.Handler handler) {
    routes.add(method + " " + path);
    switch (method) {
      case "GET" -> app.get(path, handler);
      case "POST" -> app.post(path, handler);
      case "PUT" -> app.put(path, handler);
      case "DELETE" -> app.delete(path, handler);
      default -> throw new IllegalArgumentException(method);
    }
  }

  /** Every registered route as {@code METHOD /path}, for the contract test. */
  List<String> routes() {
    return List.copyOf(routes);
  }

  private static String openApi() {
    try (var in = TwinrailServer.class.getResourceAsStream("/openapi/control-1.yaml")) {
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("The OpenAPI file is missing from the server build.", e);
    }
  }

  private void registerRoutes() {
    route(
        "GET", API + "/openapi.yaml", ctx -> ctx.contentType("application/yaml").result(openApi()));
    route("POST", "/messages", this::postMessage);
    route("POST", "/ach/files", this::postAchFile);
    route(
        "GET",
        API + "/ach/entries",
        ctx -> json(ctx, emulator.achEntries().stream().map(AchJson::entry).toList()));
    route("GET", API + "/ach/entries/{id}", this::getAchEntry);

    route("GET", API + "/health", ctx -> json(ctx, Map.of("status", "ok")));
    route(
        "POST",
        API + "/reset",
        ctx -> {
          emulator.reset();
          ctx.status(HttpStatus.NO_CONTENT);
        });

    route("GET", API + "/clock", ctx -> json(ctx, Map.of("now", emulator.now().toString())));
    route("POST", API + "/clock/advance", this::advanceClock);
    route("POST", API + "/clock/set", this::setClock);

    route("POST", API + "/accounts", this::openAccount);
    route(
        "GET",
        API + "/accounts",
        ctx -> json(ctx, emulator.accounts().stream().map(TwinrailServer::accountJson).toList()));
    route("GET", API + "/accounts/{id}", this::getAccount);
    route("GET", API + "/accounts/{id}/statement", this::statement);

    route("PUT", API + "/scenarios", this::putScenarios);
    route(
        "GET",
        API + "/scenarios",
        ctx ->
            json(
                ctx,
                emulator.scenarios().scenarios().stream().map(s -> Map.of("id", s.id())).toList()));

    route(
        "GET",
        API + "/payments",
        ctx -> json(ctx, emulator.payments().stream().map(PaymentJson::of).toList()));
    route("GET", API + "/payments/{id}", this::getPayment);

    route("POST", API + "/webhooks", this::addWebhook);
    route(
        "GET",
        API + "/webhooks",
        ctx ->
            json(
                ctx,
                emulator.webhookTargets().stream()
                    .map(t -> Map.of("id", t.id(), "url", t.url()))
                    .toList()));
    route("GET", API + "/webhooks/deliveries", ctx -> json(ctx, emulator.deliveries()));
    route(
        "DELETE",
        API + "/webhooks/{id}",
        ctx -> {
          if (emulator.removeWebhook(ctx.pathParam("id"))) {
            ctx.status(HttpStatus.NO_CONTENT);
          } else {
            notFound(
                ctx,
                "Webhook "
                    + ctx.pathParam("id")
                    + " not found. List them with GET "
                    + API
                    + "/webhooks.");
          }
        });

    route(
        "GET",
        API + "/session",
        ctx -> ctx.contentType("application/json").result(SessionFiles.write(emulator.session())));
  }

  private void postMessage(Context ctx) {
    Payment p = emulator.submit(ctx.body());
    ctx.status(
        p.state().isFinal() && p.rejection() != null
            ? HttpStatus.UNPROCESSABLE_CONTENT
            : HttpStatus.OK);
    emulator.deliverDue(sender);
    json(ctx, PaymentJson.of(p));
  }

  private void postAchFile(Context ctx) {
    var result = emulator.submitAch(ctx.body());
    ctx.status(result.rejected() ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.OK);
    json(ctx, AchJson.result(result));
  }

  private void getAchEntry(Context ctx) {
    var entry = emulator.achEntry(ctx.pathParam("id"));
    if (entry.isEmpty()) {
      notFound(
          ctx,
          "ACH entry "
              + ctx.pathParam("id")
              + " not found. List them with GET "
              + API
              + "/ach/entries.");
      return;
    }
    json(ctx, AchJson.entry(entry.get()));
  }

  private void advanceClock(Context ctx) {
    String by = body(ctx).has("by") ? body(ctx).get("by").getAsString() : null;
    if (by == null) {
      throw new IllegalArgumentException(
          "Missing 'by'. Send {\"by\": \"PT30S\"} with an ISO 8601 duration.");
    }
    clockResult(ctx, emulator.advance(Duration.parse(by)));
  }

  private void setClock(Context ctx) {
    String to = body(ctx).has("to") ? body(ctx).get("to").getAsString() : null;
    if (to == null) {
      throw new IllegalArgumentException(
          "Missing 'to'. Send {\"to\": \"2026-11-09T10:05:00Z\"} with an ISO 8601 instant.");
    }
    clockResult(ctx, emulator.setClock(Instant.parse(to)));
  }

  private void clockResult(Context ctx, Emulator.Ticked ticked) {
    List<Transition> done = ticked.payments();
    emulator.deliverDue(sender);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("now", emulator.now().toString());
    out.put(
        "transitions",
        done.stream()
            .map(t -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("paymentId", t.paymentId());
              m.put("from", t.from() == null ? null : t.from().name());
              m.put("to", t.to().name());
              m.put("at", t.at().toString());
              return m;
            })
            .toList());
    if (!ticked.ach().isEmpty()) {
      out.put("achTransitions", ticked.ach().stream().map(AchJson::transition).toList());
    }
    json(ctx, out);
  }

  private void openAccount(Context ctx) {
    JsonObject b = body(ctx);
    for (String key : List.of("id", "currency", "balance")) {
      if (!b.has(key)) {
        throw new IllegalArgumentException(
            "Missing '"
                + key
                + "'. Send {\"id\": \"acc-1\", \"currency\": \"CAD\", \"balance\": \"100.00\"}.");
      }
    }
    emulator.openAccount(
        b.get("id").getAsString(),
        b.get("currency").getAsString(),
        new BigDecimal(b.get("balance").getAsString()));
    ctx.status(HttpStatus.CREATED);
    json(ctx, accountJson(emulator.account(b.get("id").getAsString()).orElseThrow()));
  }

  private void getAccount(Context ctx) {
    var account = emulator.account(ctx.pathParam("id"));
    if (account.isEmpty()) {
      notFound(
          ctx,
          "Account "
              + ctx.pathParam("id")
              + " does not exist. Open it with POST "
              + API
              + "/accounts.");
      return;
    }
    json(ctx, accountJson(account.get()));
  }

  private void statement(Context ctx) {
    if (emulator.account(ctx.pathParam("id")).isEmpty()) {
      notFound(
          ctx,
          "Account "
              + ctx.pathParam("id")
              + " does not exist. Open it with POST "
              + API
              + "/accounts.");
      return;
    }
    ctx.contentType("application/xml").result(emulator.statement(ctx.pathParam("id")));
  }

  private void putScenarios(Context ctx) {
    emulator.setScenarios(ScenarioLoader.load(ctx.body(), "request body"));
    json(ctx, Map.of("scenarios", emulator.scenarios().scenarios().size()));
  }

  private void addWebhook(Context ctx) {
    JsonObject b = body(ctx);
    var target =
        emulator.addWebhook(
            b.has("url") ? b.get("url").getAsString() : null,
            b.has("secret") ? b.get("secret").getAsString() : null);
    ctx.status(HttpStatus.CREATED);
    json(ctx, Map.of("id", target.id(), "url", target.url()));
  }

  private void getPayment(Context ctx) {
    var payment = emulator.payment(ctx.pathParam("id"));
    if (payment.isEmpty()) {
      notFound(
          ctx,
          "Payment "
              + ctx.pathParam("id")
              + " not found. List them with GET "
              + API
              + "/payments.");
      return;
    }
    json(ctx, PaymentJson.of(payment.get()));
  }

  private void errors() {
    bad(CodecException.class);
    bad(io.github.vthattey.twinrail.core.nacha.NachaException.class);
    bad(DefinitionException.class);
    bad(LedgerException.class);
    bad(IllegalArgumentException.class);
    bad(DateTimeParseException.class);
    bad(JsonParseException.class);
    app.exception(
        Exception.class,
        (e, ctx) -> {
          ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
          json(
              ctx,
              Map.of(
                  "error",
                  "Unexpected error: "
                      + e.getClass().getSimpleName()
                      + ". This is a bug in Twinrail; please report it with the request that caused it."));
        });
  }

  private <E extends Exception> void bad(Class<E> type) {
    app.exception(
        type,
        (e, ctx) -> {
          ctx.status(HttpStatus.BAD_REQUEST);
          json(ctx, Map.of("error", String.valueOf(e.getMessage())));
        });
  }

  private static void notFound(Context ctx, String message) {
    ctx.status(HttpStatus.NOT_FOUND);
    json(ctx, Map.of("error", message));
  }

  private static JsonObject body(Context ctx) {
    var parsed = GSON.fromJson(ctx.body(), JsonObject.class);
    if (parsed == null) {
      throw new IllegalArgumentException("The request body is empty. Send a JSON object.");
    }
    return parsed;
  }

  private static void json(Context ctx, Object value) {
    ctx.contentType("application/json").result(GSON.toJson(value));
  }

  private static Map<String, Object> accountJson(Emulator.AccountView a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.id());
    m.put("currency", a.currency());
    m.put("balance", a.balance().toPlainString());
    m.put("available", a.available().toPlainString());
    return m;
  }
}
