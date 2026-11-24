package pt.ua.deti.tqs.p11.gateway;

import java.math.BigDecimal;

/**
 * A charge and its answer.
 *
 * @param reason why it was declined (card_declined, invalid_token, timeout), or null if approved
 * @param late true if the answer must be held back so the caller times out (tok_timeout)
 */
public record Charge(
    String id,
    String idempotencyKey,
    BigDecimal amount,
    String currency,
    ChargeStatus status,
    String reason,
    boolean late) {}
