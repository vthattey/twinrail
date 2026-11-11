package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.testcontainers.TwinrailClient;
import io.github.vthattey.twinrail.testcontainers.TwinrailClient.PaymentState;
import io.github.vthattey.twinrail.testcontainers.TwinrailContainer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** One container for the class; each test starts from a clean emulator with reset(). */
@Testcontainers
class PaymentFlowTest {
  private static final String IMAGE = System.getenv().getOrDefault("TWINRAIL_IMAGE", "");

  @Container
  static final TwinrailContainer TWINRAIL =
      IMAGE.isBlank() ? new TwinrailContainer() : new TwinrailContainer(IMAGE);

  private TwinrailClient twinrail;

  @BeforeEach
  void freshEmulator() {
    twinrail = TWINRAIL.client();
    twinrail.reset();
  }

  private static String creditTransfer() throws Exception {
    try (var in = PaymentFlowTest.class.getResourceAsStream("/credit-transfer.xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void aValidTransferSettlesAndMovesMoney() throws Exception {
    twinrail.openAccount("000000001", "CAD", new BigDecimal("500.00"));

    var payment = twinrail.sendMessage(creditTransfer());

    assertEquals(PaymentState.SETTLED, payment.state());
    assertEquals(new BigDecimal("374.50"), twinrail.account("000000001").balance());
  }

  @Test
  void aRejectionNamesTheRuleAndTheReasonCode() throws Exception {
    var payment = twinrail.sendMessage(creditTransfer().replace("125.50<", "0.00<"));

    assertTrue(payment.isRejected());
    assertEquals("AM01", payment.rejection().reason());
    assertEquals("EX-002", payment.rejection().rule());
  }

  @Test
  void aPaymentThatIsNeverAnsweredTimesOutWhenTheClockMoves() throws Exception {
    twinrail.setScenarios(
        """
        format: 1
        scenarios:
          - id: black-hole
            action: { type: drop }
        """);
    var payment = twinrail.sendMessage(creditTransfer());
    assertEquals(PaymentState.ACCEPTED, payment.state());

    // No sleeping: the clock is virtual, so the test jumps past the time-out instantly.
    twinrail.advance(Duration.ofSeconds(30));

    assertEquals(PaymentState.TIMED_OUT, twinrail.payment(payment.id()).state());
  }
}
