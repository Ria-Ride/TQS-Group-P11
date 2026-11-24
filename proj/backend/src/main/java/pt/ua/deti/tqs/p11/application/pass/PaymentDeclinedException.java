package pt.ua.deti.tqs.p11.application.pass;

/** BR10: the gateway refused the payment, so no pass was issued. */
public class PaymentDeclinedException extends RuntimeException {

  private final String reason;

  public PaymentDeclinedException(String reason) {
    super("The payment was declined: " + reason);
    this.reason = reason;
  }

  /** The gateway's code, such as card_declined. */
  public String reason() {
    return reason;
  }
}
