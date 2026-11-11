package io.github.vthattey.twinrail.server;

import com.google.gson.Gson;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook targets, the delivery queue and the retry schedule. Retries are scheduled on the virtual
 * clock: a failed delivery is tried again when the clock reaches its next attempt time, never after
 * a real-time wait. Not thread-safe; {@link Emulator} serialises access.
 *
 * <p>Targets must be local: loopback, private-range IP literals, or a single-label host name such
 * as a Docker network alias. This keeps the emulator from sending anything to the internet.
 */
final class Webhooks {
  /** Attempts before a delivery is given up on. Waits between them: 1s, 2s, 4s, 8s. */
  static final int MAX_ATTEMPTS = 5;

  private static final Gson GSON = new Gson();

  record Target(String id, String url, String secret) {}

  /** One event for one target. */
  static final class Delivery {
    final String id;
    final Target target;
    final String eventId;
    final String body;
    int attempts;
    Instant nextAttemptAt;
    String state = "PENDING";
    int lastStatus;

    Delivery(String id, Target target, String eventId, String body, Instant firstAttempt) {
      this.id = id;
      this.target = target;
      this.eventId = eventId;
      this.body = body;
      this.nextAttemptAt = firstAttempt;
    }

    Map<String, Object> view() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", id);
      m.put("eventId", eventId);
      m.put("target", target.id());
      m.put("state", state);
      m.put("attempts", attempts);
      m.put("lastStatus", lastStatus);
      m.put("nextAttemptAt", "PENDING".equals(state) ? nextAttemptAt.toString() : null);
      return m;
    }
  }

  private final List<Target> targets = new ArrayList<>();
  private final List<Delivery> deliveries = new ArrayList<>();
  private int targetCounter;
  private int eventCounter;

  Target register(String url, String secret) {
    checkLocal(url);
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException(
          "Missing 'secret'. Webhooks are signed with it; send"
              + " {\"url\": \"http://localhost:9000/hook\", \"secret\": \"any-string\"}.");
    }
    Target t = new Target("WH-%03d".formatted(++targetCounter), url, secret);
    targets.add(t);
    return t;
  }

  boolean remove(String id) {
    return targets.removeIf(t -> t.id().equals(id));
  }

  List<Target> targets() {
    return List.copyOf(targets);
  }

  List<Delivery> deliveries() {
    return List.copyOf(deliveries);
  }

  /** Queues one delivery per target for an event. The first attempt is due at {@code time}. */
  void publish(String type, Instant time, Map<String, Object> payload) {
    if (targets.isEmpty()) {
      return;
    }
    String eventId = "EVT-%06d".formatted(++eventCounter);
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("id", eventId);
    envelope.put("type", type);
    envelope.put("time", time.toString());
    envelope.put("payload", payload);
    String body = GSON.toJson(envelope);
    for (Target t : targets) {
      deliveries.add(new Delivery(eventId + "/" + t.id(), t, eventId, body, time));
    }
  }

  /** Deliveries whose next attempt is due at or before {@code now}, oldest first. */
  List<Delivery> due(Instant now) {
    return deliveries.stream()
        .filter(d -> "PENDING".equals(d.state) && !d.nextAttemptAt.isAfter(now))
        .toList();
  }

  /** Records the outcome of an attempt made at virtual time {@code now}. */
  void record(Delivery d, int status, Instant now) {
    d.attempts++;
    d.lastStatus = status;
    if (status >= 200 && status < 300) {
      d.state = "DELIVERED";
    } else if (d.attempts >= MAX_ATTEMPTS) {
      d.state = "FAILED";
    } else {
      d.nextAttemptAt = now.plus(Duration.ofSeconds(1L << (d.attempts - 1)));
    }
  }

  /** The HTTP headers for a delivery, including the HMAC-SHA256 signature of the body. */
  static Map<String, String> headers(Delivery d) {
    Map<String, String> h = new LinkedHashMap<>();
    h.put("Content-Type", "application/json");
    h.put("Twinrail-Event-Id", d.eventId);
    h.put("Twinrail-Signature", "sha256=" + sign(d.target.secret(), d.body));
    return h;
  }

  static String sign(String secret, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : raw) {
        hex.append("%02x".formatted(b));
      }
      return hex.toString();
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 is not available in this JVM.", e);
    }
  }

  static void checkLocal(String url) {
    URI uri;
    try {
      uri = new URI(url == null ? "" : url);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(
          "'" + url + "' is not a valid URL. Example: http://localhost:9000/hook");
    }
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (host == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
      throw new IllegalArgumentException(
          "'"
              + url
              + "' must be an http or https URL with a host. Example: http://localhost:9000/hook");
    }
    if (!isLocalHost(host.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException(
          "Webhook target host '"
              + host
              + "' is not local. Twinrail never sends traffic to the internet: use localhost, a"
              + " private IP address, host.docker.internal or a single-label host name such as a"
              + " Docker network alias.");
    }
  }

  private static boolean isLocalHost(String host) {
    if (host.equals("localhost") || host.equals("host.docker.internal")) {
      return true;
    }
    String bare =
        host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    boolean ipv4 = bare.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    if (ipv4 || bare.contains(":")) {
      try {
        InetAddress a = InetAddress.getByName(bare); // a literal: no DNS lookup happens
        return a.isLoopbackAddress()
            || a.isSiteLocalAddress()
            || a.isLinkLocalAddress()
            || (bare.contains(":") && (bare.startsWith("fc") || bare.startsWith("fd")));
      } catch (java.net.UnknownHostException e) {
        return false;
      }
    }
    return !host.contains(".");
  }
}
