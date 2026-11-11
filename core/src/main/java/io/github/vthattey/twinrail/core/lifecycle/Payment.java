package io.github.vthattey.twinrail.core.lifecycle;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An immutable snapshot of a payment.
 *
 * @param id the emulator's payment id, for example {@code PMT-000001}
 * @param settleAt when it will settle, or {@code null} if it will not settle
 * @param timeoutAt when it times out if still unsettled
 * @param closedAt when it reached a final state, or {@code null}
 * @param rejection set only when rejected
 */
public record Payment(
    String id,
    String messageType,
    String endToEndId,
    String uetr,
    String debtorAccount,
    String creditorAccount,
    BigDecimal amount,
    String currency,
    PaymentState state,
    Instant receivedAt,
    Instant settleAt,
    Instant timeoutAt,
    Instant closedAt,
    Rejection rejection) {

  Payment closed(PaymentState to, Instant at) {
    return new Payment(
        id,
        messageType,
        endToEndId,
        uetr,
        debtorAccount,
        creditorAccount,
        amount,
        currency,
        to,
        receivedAt,
        settleAt,
        timeoutAt,
        at,
        rejection);
  }
}
