package io.github.vthattey.twinrail.core.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One booking on one account.
 *
 * @param sequence position in the ledger, starting at 1; defines the booking order
 * @param bookedAt virtual time of the booking
 * @param paymentId the emulator's payment id
 * @param endToEndId the end-to-end id from the payment message, or {@code null}
 */
public record Entry(
    long sequence,
    Instant bookedAt,
    String account,
    Side side,
    BigDecimal amount,
    String currency,
    String paymentId,
    String endToEndId) {}
