package io.github.vthattey.twinrail.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * The static files of the UI: the vendored htmx script (no CDN: the emulator makes no network
 * calls) and the stylesheet. Only the names listed here can be served.
 */
public final class Assets {
  /** A file and its media type. */
  public record Asset(byte[] bytes, String contentType) {}

  private static final Map<String, String> TYPES =
      Map.of("htmx.min.js", "text/javascript; charset=utf-8", "ui.css", "text/css; charset=utf-8");

  private Assets() {}

  public static Optional<Asset> find(String name) {
    String type = TYPES.get(name);
    if (type == null) {
      return Optional.empty();
    }
    try (InputStream in = Assets.class.getResourceAsStream("/ui/static/" + name)) {
      if (in == null) {
        throw new IllegalStateException("The UI file " + name + " is missing from the build.");
      }
      return Optional.of(new Asset(in.readAllBytes(), type));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read the UI file " + name + ".", e);
    }
  }
}
