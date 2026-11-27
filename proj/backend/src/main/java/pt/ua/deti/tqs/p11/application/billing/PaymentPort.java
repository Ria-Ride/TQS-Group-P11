package pt.ua.deti.tqs.p11.application.billing;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * Port: the payment gateway, owned by the core (K2). The adapter is the only code that knows the
 * gateway's protocol, so another gateway means another adapter and no change here.
 *
 * <p>Passes are charged at once (US03). A booking's deposit is held before the ride (authorize),
 * then taken up to the overtime charge with the rest given back (capture), or given back whole
 * (release). No method throws for a gateway problem: an answer that does not come in time is a
 * time-out result, and every call is safe to repeat with the same payment id.
 */
public interface PaymentPort {

  /**
   * Takes the amount now. Sending the same {@link ChargeRequest#paymentId()} again is safe: the
   * gateway charges at most once per id.
   */
  ChargeResult charge(ChargeRequest request);

  /** BR11: holds the deposit before any vehicle state change. */
  DepositResult authorize(DepositRequest request);

  /** BR13: takes the amount from the deposit; the gateway gives the rest back in the same step. */
  DepositResult capture(UUID paymentId, Money amount);

  /** BR13: gives the whole deposit back. A deposit the gateway never held is approved too. */
  DepositResult release(UUID paymentId);
}
