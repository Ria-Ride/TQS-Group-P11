package pt.ua.deti.tqs.p11.gateway;

import java.math.BigDecimal;

/**
 * A deposit and where it stands.
 *
 * @param captured the amount taken, or null unless captured
 * @param reason why it was declined (card_declined, invalid_token), or null
 * @param late true if the answer to the authorization must be held back so the caller times out
 *     (tok_timeout); the amount is held all the same
 */
public record Authorization(
    String id,
    String idempotencyKey,
    BigDecimal amount,
    String currency,
    AuthorizationStatus status,
    BigDecimal captured,
    String reason,
    boolean late) {

  /** What was given back: the rest after a capture, the whole amount after a release. */
  public BigDecimal released() {
    return switch (status) {
      case CAPTURED -> amount.subtract(captured);
      case RELEASED -> amount;
      case AUTHORIZED, DECLINED -> BigDecimal.ZERO;
    };
  }

  Authorization capturing(BigDecimal taken) {
    return new Authorization(
        id, idempotencyKey, amount, currency, AuthorizationStatus.CAPTURED, taken, reason, late);
  }

  Authorization releasing() {
    return new Authorization(
        id, idempotencyKey, amount, currency, AuthorizationStatus.RELEASED, null, reason, late);
  }
}