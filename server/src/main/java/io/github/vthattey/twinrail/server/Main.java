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
    if (args.length > 0 && args[0].equals("--warmup")) {
      warmup();
      return;
    }
    int port = Integer.parseInt(env("TWINRAIL_PORT", "8080"));
    var config =
        new Emulator.Config(
            Long.parseLong(env("TWINRAIL_SEED", "1")),
            Instant.parse(env("TWINRAIL_START", "2026-01-01T00:00:00Z")),
            Duration.parse(env("TWINRAIL_TIMEOUT", "PT30S")),
            loadPack(System.getenv("TWINRAIL_RULE_PACK")));
    warmCodec();
    new TwinrailServer(new Emulator(config)).start(port);
  }

  /**
   * Loads and exercises the classes a real run uses, then exits. The Docker build runs this once
   * with an AppCDS archive switch to create the class archive that makes start-up faster.
   */
  private static void warmup() throws IOException {
    var emulator =
        new Emulator(
            new Emulator.Config(
                1, Instant.parse("2026-01-01T00:00:00Z"), Duration.ofSeconds(30), loadPack(null)));
    var server = new TwinrailServer(emulator, (target, headers, body) -> 200).start(0);
    try {
      var client = java.net.http.HttpClient.newHttpClient();
      var base = "http://localhost:" + server.port();
      for (var path : java.util.List.of("/_twinrail/health", "/_twinrail/clock", "/ui/live")) {
        client.send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path)).build(),
            java.net.http.HttpResponse.BodyHandlers.discarding());
      }
      client.send(
          java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/messages"))
              .POST(java.net.http.HttpRequest.BodyPublishers.ofString(WARMUP_MESSAGE))
              .build(),
          java.net.http.HttpResponse.BodyHandlers.discarding());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      server.stop();
    }
  }

  /** Creates the JAXB context now, so the first request is not the slow one. */
  private static void warmCodec() {
    var codec = new io.github.vthattey.twinrail.core.codec.MessageCodec();
    codec.write(codec.parse(WARMUP_MESSAGE).mx());
  }

  private static final String WARMUP_MESSAGE =
      "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\"><FIToFICstmrCdtTrf>"
          + "<GrpHdr><MsgId>WARMUP</MsgId><NbOfTxs>1</NbOfTxs></GrpHdr><CdtTrfTxInf><PmtId>"
          + "<EndToEndId>WARMUP</EndToEndId></PmtId><IntrBkSttlmAmt Ccy=\"CAD\">1.00"
          + "</IntrBkSttlmAmt></CdtTrfTxInf></FIToFICstmrCdtTrf></Document>";

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
