package pt.ua.deti.tqs.p11.application.booking;

/**
 * BR11: the gateway gave no answer in time, so no booking was created; the deposit was released in
 * case it was held. Trying again is safe.
 */
public class DepositTimedOutException extends RuntimeException {

  public DepositTimedOutException() {
    super("The deposit got no answer in time");
  }
}
