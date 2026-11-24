package pt.ua.deti.tqs.p11.application.pass;

/** BR10: the gateway gave no answer in time, so the payment counts as not made and no pass. */
public class PaymentTimedOutException extends RuntimeException {

  public PaymentTimedOutException() {
    super("The payment got no answer in time");
  }
}
