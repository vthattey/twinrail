package io.github.vthattey.twinrail.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.vthattey.twinrail.core.codec.CodecException;
import io.github.vthattey.twinrail.core.definition.DefinitionException;
import io.github.vthattey.twinrail.core.ledger.LedgerException;
import io.github.vthattey.twinrail.core.lifecycle.Payment;
import io.github.vthattey.twinrail.core.lifecycle.Rejection;
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

  public TwinrailServer(Emulator emulator) {
    this.emulator = emulator;
    this.app = Javalin.create(config -> config.showJavalinBanner = false);
    routes();
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

  private void routes() {
    app.post("/messages", this::postMessage);

    app.get(API + "/health", ctx -> json(ctx, Map.of("status", "ok")));
    app.post(
        API + "/reset",
        ctx -> {
          emulator.reset();
          ctx.status(HttpStatus.NO_CONTENT);
        });

    app.get(API + "/clock", ctx -> json(ctx, Map.of("now", emulator.now().toString())));
    app.post(API + "/clock/advance", this::advanceClock);
    app.post(API + "/clock/set", this::setClock);

    app.post(API + "/accounts", this::openAccount);
    app.get(API + "/accounts", ctx -> json(ctx, emulator.accounts()));
    app.get(API + "/accounts/{id}", this::getAccount);
    app.get(API + "/accounts/{id}/statement", this::statement);

    app.put(API + "/scenarios", this::putScenarios);
    app.get(
        API + "/scenarios",
        ctx ->
            json(
                ctx,
                emulator.scenarios().scenarios().stream().map(s -> Map.of("id", s.id())).toList()));

    app.get(
        API + "/payments",
        ctx -> json(ctx, emulator.payments().stream().map(TwinrailServer::view).toList()));
    app.get(API + "/payments/{id}", this::getPayment);

    app.get(
        API + "/session",
        ctx -> ctx.contentType("application/json").result(SessionFiles.write(emulator.session())));
  }

  private void postMessage(Context ctx) {
    Payment p = emulator.submit(ctx.body());
    ctx.status(
        p.state().isFinal() && p.rejection() != null
            ? HttpStatus.UNPROCESSABLE_CONTENT
            : HttpStatus.OK);
    json(ctx, view(p));
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

  private void clockResult(Context ctx, List<Transition> done) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("now", emulator.now().toString());
    out.put("transitions", done);
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
    json(ctx, emulator.account(b.get("id").getAsString()).orElseThrow());
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
    json(ctx, account.get());
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
    json(ctx, view(payment.get()));
  }

  private void errors() {
    bad(CodecException.class);
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

  private static Map<String, Object> view(Payment p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.id());
    m.put("state", p.state().name());
    m.put("endToEndId", p.endToEndId());
    m.put("uetr", p.uetr());
    m.put("debtorAccount", p.debtorAccount());
    m.put("creditorAccount", p.creditorAccount());
    m.put("amount", p.amount() == null ? null : p.amount().toPlainString());
    m.put("currency", p.currency());
    m.put("receivedAt", p.receivedAt().toString());
    m.put("settleAt", p.settleAt() == null ? null : p.settleAt().toString());
    m.put("timeoutAt", p.timeoutAt().toString());
    m.put("closedAt", p.closedAt() == null ? null : p.closedAt().toString());
    Rejection r = p.rejection();
    if (r != null) {
      m.put(
          "rejection",
          Map.of("rule", r.ruleId(), "reason", r.reason(), "text", r.text(), "source", r.source()));
    }
    return m;
  }
}
