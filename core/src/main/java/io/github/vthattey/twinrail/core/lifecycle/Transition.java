package io.github.vthattey.twinrail.core.lifecycle;

import java.time.Instant;

/**
 * A state change, with the virtual time it happened at.
 *
 * @param from {@code null} for the first state a payment is given
 */
public record Transition(String paymentId, PaymentState from, PaymentState to, Instant at) {}
