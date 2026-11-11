package io.github.vthattey.twinrail.server;

import io.github.vthattey.twinrail.core.rules.RulePack;
import io.github.vthattey.twinrail.core.rules.RulePackLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Starts the emulator. Settings come from environment variables: {@code TWINRAIL_PORT} (default
 * 8080), {@code TWINRAIL_SEED} (1), {@code TWINRAIL_START} (2026-01-01T00:00:00Z), {@code
 * TWINRAIL_TIMEOUT} (PT30S) and {@code TWINRAIL_RULE_PACK} (a YAML file; default is the bundled
 * illustrative example pack).
 */
public final class Main {
  private Main() {}

  public static void main(String[] args) throws IOException {
    int port = Integer.parseInt(env("TWINRAIL_PORT", "8080"));
    var config =
        new Emulator.Config(
            Long.parseLong(env("TWINRAIL_SEED", "1")),
            Instant.parse(env("TWINRAIL_START", "2026-01-01T00:00:00Z")),
            Duration.parse(env("TWINRAIL_TIMEOUT", "PT30S")),
            loadPack(System.getenv("TWINRAIL_RULE_PACK")));
    new TwinrailServer(new Emulator(config)).start(port);
  }

  static RulePack loadPack(String file) throws IOException {
    if (file != null && !file.isBlank()) {
      return RulePackLoader.loadFile(Path.of(file));
    }
    try (InputStream in = Main.class.getResourceAsStream("/packs/example.yaml")) {
      if (in == null) {
        throw new IllegalStateException(
            "No rule pack found. Set TWINRAIL_RULE_PACK to a rule-pack YAML file.");
      }
      return RulePackLoader.load(
          new String(in.readAllBytes(), StandardCharsets.UTF_8), "example.yaml");
    }
  }

  private static String env(String name, String fallback) {
    String v = System.getenv(name);
    return v == null || v.isBlank() ? fallback : v;
  }
}
