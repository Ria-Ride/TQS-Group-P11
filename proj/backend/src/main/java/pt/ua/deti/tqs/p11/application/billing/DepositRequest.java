package pt.ua.deti.tqs.p11.application.billing;

import java.util.Objects;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * A deposit to hold at the gateway (BR11).
 *
 * @param paymentId our id of the deposit: the idempotency key, and the name of the deposit in
 *     capture and release, so it can be released even when the authorization got no answer
 * @param paymentToken what the tourist pays with; in the simulated gateway, a test card
 */
public record DepositRequest(UUID paymentId, Money amount, String paymentToken) {

  public DepositRequest {
    Objects.requireNonNull(paymentId, "paymentId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(paymentToken, "paymentToken");
  }
}
