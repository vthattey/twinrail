package io.github.vthattey.twinrail.testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.testcontainers.TwinrailClient.PaymentState;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class TwinrailContainerTest {
  @Test
  void theDefaultImageMatchesTheModuleVersion() {
    // The build version is a snapshot, which has no published image, so the tag is latest.
    assertEquals("ghcr.io/vthattey/twinrail:latest", TwinrailContainer.defaultImage());
  }

  @Test
  void configurationNeedsNoDocker() {
    var container =
        new TwinrailContainer("twinrail:test")
            .withSeed(42)
            .withStart(Instant.parse("2026-11-10T10:00:00Z"))
            .withPaymentTimeout(Duration.ofMinutes(2));

    assertTrue(container.getExposedPorts().contains(TwinrailContainer.PORT));
    assertEquals("42", container.getEnvMap().get("TWINRAIL_SEED"));
    assertEquals("2026-11-10T10:00:00Z", container.getEnvMap().get("TWINRAIL_START"));
    assertEquals("PT2M", container.getEnvMap().get("TWINRAIL_TIMEOUT"));
  }

  @Test
  void aRulePackIsCopiedInAndPointedAt() {
    var container = new TwinrailContainer("twinrail:test").withRulePack("format: 1\n");
    assertEquals("/etc/twinrail/rule-pack.yaml", container.getEnvMap().get("TWINRAIL_RULE_PACK"));
  }

  /**
   * Runs the real image. Set TWINRAIL_IMAGE (for example {@code twinrail:ci}, built from the
   * Dockerfile) and have Docker running; otherwise this test is skipped.
   */
  @Test
  @EnabledIfEnvironmentVariable(named = "TWINRAIL_IMAGE", matches = ".+")
  void theRealImageStartsAndSettlesAPayment() throws Exception {
    try (var container =
        new TwinrailContainer(System.getenv("TWINRAIL_IMAGE"))
            .withSeed(7)
            .withStart(Instant.parse("2026-11-10T10:00:00Z"))) {
      container.start();
      var client = container.client();
      assertTrue(client.isHealthy());
      assertEquals(Instant.parse("2026-11-10T10:00:00Z"), client.now());

      client.openAccount("000000001", "CAD", new BigDecimal("500.00"));
      String xml;
      try (var in = TwinrailContainerTest.class.getResourceAsStream("/pacs008.xml")) {
        xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      var payment = client.sendMessage(xml);
      assertEquals(PaymentState.SETTLED, payment.state());
      assertEquals(new BigDecimal("374.50"), client.account("000000001").balance());

      client.reset();
      assertTrue(client.payments().isEmpty());
    }
  }
}
