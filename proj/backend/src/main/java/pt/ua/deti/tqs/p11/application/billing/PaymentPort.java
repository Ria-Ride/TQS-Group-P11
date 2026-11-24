package pt.ua.deti.tqs.p11.application.billing;

/**
 * Port: the payment gateway, owned by the core (K2). The adapter is the only code that knows the
 * gateway's protocol, so another gateway means another adapter and no change here.
 *
 * <p>Only charging exists now (passes, US03). Deposits (US04 to US06) add authorize, capture and
 * release here, with the same three kinds of answer.
 */
public interface PaymentPort {

  /**
   * Takes the amount now. Never throws for a gateway problem: an answer that does not come in time
   * is a {@link ChargeResult.TimedOut}. Sending the same {@link ChargeRequest#paymentId()} again is
   * safe: the gateway charges at most once per id.
   */
  ChargeResult charge(ChargeRequest request);
}
