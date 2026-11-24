package pt.ua.deti.tqs.p11.domain.billing;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One attempt to charge an account, kept whether or not it was approved, so every purchase can be
 * traced. BR10: only a CAPTURED payment has a pass; a DECLINED one (refused, or no answer in time)
 * has a reason and no pass. No card data is kept, only the gateway's reference.
 *
 * @param reference the gateway's id of the charge; null unless captured
 * @param reason why no money was taken (card_declined, timeout, ...); null if captured
 * @param pass the pass the payment paid for; null unless captured
 */
public record Payment(
    UUID id,
    UUID accountId,
    Money amount,
    PaymentState state,
    String reference,
    String reason,
    UUID pass,
    Instant at) {

  public Payment {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(at, "at");
    boolean captured = state == PaymentState.CAPTURED;
    if (captured && (reference == null || pass == null)) {
      throw new IllegalArgumentException("A captured payment needs its reference and its pass");
    }
    if (!captured && (reason == null || pass != null)) {
      throw new IllegalArgumentException("A declined payment has a reason and no pass");
    }
  }

  public static Payment captured(
      UUID id, UUID accountId, Money amount, String reference, UUID passId, Instant at) {
    return new Payment(id, accountId, amount, PaymentState.CAPTURED, reference, null, passId, at);
  }

  public static Payment declined(UUID id, UUID accountId, Money amount, String reason, Instant at) {
    return new Payment(id, accountId, amount, PaymentState.DECLINED, null, reason, null, at);
  }

  public Optional<String> gatewayReference() {
    return Optional.ofNullable(reference);
  }

  public Optional<String> failureReason() {
    return Optional.ofNullable(reason);
  }

  public Optional<UUID> passId() {
    return Optional.ofNullable(pass);
  }
}
