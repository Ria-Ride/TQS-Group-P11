package pt.ua.deti.tqs.p11.application.billing;

import java.util.Objects;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * A charge to send to the gateway.
 *
 * @param paymentId our id of the payment, sent as the idempotency key
 * @param paymentToken what the tourist pays with; in the simulated gateway, a test card
 */
public record ChargeRequest(UUID paymentId, Money amount, String paymentToken) {

  public ChargeRequest {
    Objects.requireNonNull(paymentId, "paymentId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(paymentToken, "paymentToken");
  }
}
