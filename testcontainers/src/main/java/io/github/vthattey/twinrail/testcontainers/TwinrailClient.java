package io.github.vthattey.twinrail.testcontainers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A typed client for the Twinrail control API (see the OpenAPI file served at {@code
 * /_twinrail/openapi.yaml}). Time in the emulator is virtual: nothing happens until you move the
 * clock with {@link #advance} or {@link #setClock}, which also applies what became due and sends
 * due webhooks.
 *
 * <p>Calls that the emulator refuses throw {@link TwinrailException} with its explanation. A
 * payment or ACH file that is <em>rejected</em> is not an error: it comes back as a result you can
 * inspect.
 */
public final class TwinrailClient {
  private static final Gson GSON =
      new GsonBuilder()
          .registerTypeAdapter(Instant.class, new InstantAdapter())
          .disableHtmlEscaping()
          .create();

  private final URI base;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  /**
   * @param base where the emulator listens, for example {@code http://localhost:8080}
   */
  public TwinrailClient(URI base) {
    String text = base.toString();
    this.base = URI.create(text.endsWith("/") ? text.substring(0, text.length() - 1) : text);
  }

  // --- types -----------------------------------------------------------------------------

  /** The state of an ISO 20022 payment. */
  public enum PaymentState {
    ACCEPTED,
    SETTLED,
    REJECTED,
    TIMED_OUT
  }

  /** The state of an ACH entry. */
  public enum AchState {
    ACCEPTED,
    SETTLED,
    RETURNED
  }

  /**
   * Why a payment was rejected: the rule, the ISO 20022 reason code, and where the rule comes from.
   */
  public record Rejection(String rule, String reason, String text, String source) {}

  /** A credit transfer and where it is in its life. {@code rejection} is set only if rejected. */
  public record Payment(
      String id,
      PaymentState state,
      String endToEndId,
      String uetr,
      String debtorAccount,
      String creditorAccount,
      BigDecimal amount,
      String currency,
      Instant receivedAt,
      Instant settleAt,
      Instant timeoutAt,
      Instant closedAt,
      Rejection rejection) {
    public boolean isRejected() {
      return state == PaymentState.REJECTED;
    }
  }

  /** An account's balance and the funds not reserved for accepted payments. */
  public record Account(String id, String currency, BigDecimal balance, BigDecimal available) {}

  /** A payment state change caused by moving the clock. */
  public record Transition(String paymentId, PaymentState from, PaymentState to, Instant at) {}

  /** An ACH entry state change. {@code kind} is accepted, settled, returned or change. */
  public record AchTransition(
      String entryId, AchState from, AchState to, String kind, Instant at) {}

  /** What a clock move caused. */
  public record ClockResult(
      Instant now, List<Transition> transitions, List<AchTransition> achTransitions) {
    public ClockResult {
      transitions = transitions == null ? List.of() : List.copyOf(transitions);
      achTransitions = achTransitions == null ? List.of() : List.copyOf(achTransitions);
    }
  }

  /**
   * A return of an ACH entry. {@code timeframe} is the return time frame as the emulator's code
   * table source states it; {@code addendaRecord} is the 94-character type 99 record.
   */
  public record AchReturn(
      String code,
      String title,
      String timeframe,
      String source,
      String text,
      Instant at,
      String addendaRecord) {}

  /** A notification of change on an ACH entry; {@code addendaRecord} is the type 98 record. */
  public record AchChange(
      String code,
      String title,
      String source,
      Map<String, String> corrected,
      Instant at,
      String addendaRecord) {}

  /** An ACH entry. {@code returned} and {@code change} are set only when they happened. */
  public record AchEntry(
      String id,
      AchState state,
      String traceNumber,
      int batchNumber,
      String companyName,
      String secCode,
      String transactionCode,
      String routingNumber,
      String accountNumber,
      BigDecimal amount,
      String individualName,
      Instant receivedAt,
      Instant settleAt,
      Instant closedAt,
      @SerializedName("return") AchReturn returned,
      AchChange change) {}

  /**
   * Something wrong with an ACH file as a whole, with a stable id such as nacha.batch.total-credit.
   */
  public record AchProblem(String id, int line, String text) {}

  /** The outcome of sending an ACH file: entries, or (if the file was refused whole) problems. */
  public record AchFileResult(List<AchEntry> entries, List<AchProblem> problems) {
    public AchFileResult {
      entries = entries == null ? List.of() : List.copyOf(entries);
      problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public boolean rejected() {
      return !problems.isEmpty();
    }
  }

  /** A registered webhook target. The secret is never returned. */
  public record WebhookTarget(String id, String url) {}

  /** A webhook delivery. {@code nextAttemptAt} is set only while it is pending. */
  public record Delivery(
      String id,
      String eventId,
      String target,
      String state,
      int attempts,
      int lastStatus,
      Instant nextAttemptAt) {}

  // --- clock and state --------------------------------------------------------------------

  /** Moves virtual time forward by {@code by} and applies everything that became due. */
  public ClockResult advance(Duration by) {
    return post(
        "/_twinrail/clock/advance",
        "application/json",
        json("by", by.toString()),
        ClockResult.class);
  }

  /** Moves virtual time to an instant (never backwards) and applies everything that became due. */
  public ClockResult setClock(Instant to) {
    return post(
        "/_twinrail/clock/set", "application/json", json("to", to.toString()), ClockResult.class);
  }

  /** The current virtual time. */
  public Instant now() {
    return Instant.parse(get("/_twinrail/clock", JsonObject.class).get("now").getAsString());
  }

  /** Back to the start state: clock, ids, accounts, payments, scenarios, webhooks, recording. */
  public void reset() {
    exchange("POST", "/_twinrail/reset", null, null, 204);
  }

  /** True if the emulator answers its health check. */
  public boolean isHealthy() {
    try {
      return get("/_twinrail/health", JsonObject.class).get("status").getAsString().equals("ok");
    } catch (TwinrailException e) {
      return false;
    }
  }

  // --- payments ---------------------------------------------------------------------------

  /**
   * Sends a {@code pacs.008}. A rejected payment is returned (see {@link Payment#rejection()}), not
   * thrown.
   */
  public Payment sendMessage(String xml) {
    Response r = exchange("POST", "/messages", "application/xml", xml, 200, 422);
    return GSON.fromJson(r.body(), Payment.class);
  }

  public List<Payment> payments() {
    return get("/_twinrail/payments", new TypeToken<List<Payment>>() {}.getType());
  }

  public Payment payment(String id) {
    return get("/_twinrail/payments/" + id, Payment.class);
  }

  // --- accounts ---------------------------------------------------------------------------

  /** Opens an account that cannot be overdrawn. */
  public Account openAccount(String id, String currency, BigDecimal balance) {
    JsonObject body = new JsonObject();
    body.addProperty("id", id);
    body.addProperty("currency", currency);
    body.addProperty("balance", balance.toPlainString());
    return post("/_twinrail/accounts", "application/json", body.toString(), Account.class, 201);
  }

  public Account account(String id) {
    return get("/_twinrail/accounts/" + id, Account.class);
  }

  /** The account, or empty if it does not exist. */
  public Optional<Account> findAccount(String id) {
    try {
      return Optional.of(account(id));
    } catch (TwinrailException e) {
      if (e.status() == 404) {
        return Optional.empty();
      }
      throw e;
    }
  }

  public List<Account> accounts() {
    return get("/_twinrail/accounts", new TypeToken<List<Account>>() {}.getType());
  }

  /** The account's {@code camt.053.001.08} statement as XML. */
  public String statement(String accountId) {
    return exchange("GET", "/_twinrail/accounts/" + accountId + "/statement", null, null, 200)
        .body();
  }

  // --- scenarios --------------------------------------------------------------------------

  /**
   * Replaces the scenarios used for payments and entries received from now on.
   *
   * @param yaml a scenario file (see docs/scenarios.md)
   * @return how many scenarios were loaded
   */
  public int setScenarios(String yaml) {
    return put("/_twinrail/scenarios", "application/yaml", yaml, JsonObject.class)
        .get("scenarios")
        .getAsInt();
  }

  // --- ACH --------------------------------------------------------------------------------

  /**
   * Sends an ACH file. A file refused whole (wrong control totals and so on) comes back with {@link
   * AchFileResult#problems()}; a file that is not an ACH file at all throws.
   */
  public AchFileResult sendAchFile(String file) {
    Response r = exchange("POST", "/ach/files", "text/plain", file, 200, 422);
    return GSON.fromJson(r.body(), AchFileResult.class);
  }

  public List<AchEntry> achEntries() {
    return get("/_twinrail/ach/entries", new TypeToken<List<AchEntry>>() {}.getType());
  }

  public AchEntry achEntry(String id) {
    return get("/_twinrail/ach/entries/" + id, AchEntry.class);
  }

  // --- webhooks ---------------------------------------------------------------------------

  /**
   * Registers a webhook target. It must be local (localhost, a private address, {@code
   * host.docker.internal} or a single-label host name); anything else is refused.
   */
  public WebhookTarget registerWebhook(String url, String secret) {
    JsonObject body = new JsonObject();
    body.addProperty("url", url);
    body.addProperty("secret", secret);
    return post(
        "/_twinrail/webhooks", "application/json", body.toString(), WebhookTarget.class, 201);
  }

  public List<WebhookTarget> webhooks() {
    return get("/_twinrail/webhooks", new TypeToken<List<WebhookTarget>>() {}.getType());
  }

  public void removeWebhook(String id) {
    exchange("DELETE", "/_twinrail/webhooks/" + id, null, null, 204);
  }

  public List<Delivery> deliveries() {
    return get("/_twinrail/webhooks/deliveries", new TypeToken<List<Delivery>>() {}.getType());
  }

  /** The recorded session (seed, start time, every message and clock event) as a session file. */
  public String session() {
    return exchange("GET", "/_twinrail/session", null, null, 200).body();
  }

  // --- plumbing ---------------------------------------------------------------------------

  private record Response(int status, String body) {}

  private <T> T get(String path, java.lang.reflect.Type type) {
    return GSON.fromJson(exchange("GET", path, null, null, 200).body(), type);
  }

  private <T> T get(String path, Class<T> type) {
    return GSON.fromJson(exchange("GET", path, null, null, 200).body(), type);
  }

  private <T> T post(String path, String contentType, String body, Class<T> type, int... ok) {
    return GSON.fromJson(
        exchange("POST", path, contentType, body, ok.length == 0 ? new int[] {200} : ok).body(),
        type);
  }

  private <T> T put(String path, String contentType, String body, Class<T> type) {
    return GSON.fromJson(exchange("PUT", path, contentType, body, 200).body(), type);
  }

  private static String json(String key, String value) {
    JsonObject o = new JsonObject();
    o.addProperty(key, value);
    return o.toString();
  }

  private Response exchange(
      String method, String path, String contentType, String body, int... ok) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
    if (contentType != null) {
      request.header("Content-Type", contentType);
    }
    request.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
    HttpResponse<String> response;
    try {
      response = http.send(request.build(), BodyHandlers.ofString());
    } catch (IOException e) {
      throw new TwinrailException(
          "Cannot reach Twinrail at "
              + base
              + " ("
              + e.getClass().getSimpleName()
              + "). Is the container started?",
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TwinrailException("Interrupted while calling Twinrail.", e);
    }
    for (int status : ok) {
      if (response.statusCode() == status) {
        return new Response(status, response.body());
      }
    }
    throw new TwinrailException(response.statusCode(), errorText(response));
  }

  private static String errorText(HttpResponse<String> response) {
    try {
      JsonObject o = GSON.fromJson(response.body(), JsonObject.class);
      if (o != null && o.has("error")) {
        return o.get("error").getAsString();
      }
    } catch (JsonParseException ignored) {
      // fall through: not the emulator's error format
    }
    return "Twinrail answered " + response.statusCode() + ": " + response.body();
  }

  /** Reads and writes {@link Instant} as ISO 8601 text. */
  private static final class InstantAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
      out.value(value == null ? null : value.toString());
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return Instant.parse(in.nextString());
    }
  }
}
