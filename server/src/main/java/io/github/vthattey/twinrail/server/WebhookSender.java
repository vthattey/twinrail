package io.github.vthattey.twinrail.server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

/** Posts a webhook body. Returns the HTTP status, or -1 if the target could not be reached. */
public interface WebhookSender {
  int post(URI target, Map<String, String> headers, String body);

  /** Plain HTTP client with short time-outs and no redirects. */
  static WebhookSender http() {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    return (target, headers, body) -> {
      try {
        var req = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(2));
        headers.forEach(req::header);
        return client
            .send(
                req.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                BodyHandlers.discarding())
            .statusCode();
      } catch (IOException e) {
        return -1;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return -1;
      }
    };
  }
}
