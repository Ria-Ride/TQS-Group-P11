package pt.ua.deti.tqs.p11.gateway;

/** The idempotency key was already used for a charge with another amount, currency or card. */
public class IdempotencyKeyReusedException extends RuntimeException {

  public IdempotencyKeyReusedException(String key) {
    super("Idempotency key %s was already used for a different charge".formatted(key));
  }
}
