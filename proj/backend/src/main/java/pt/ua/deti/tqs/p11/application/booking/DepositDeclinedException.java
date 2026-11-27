package pt.ua.deti.tqs.p11.application.booking;

/** BR11: the gateway refused to hold the deposit, so no booking was created. */
public class DepositDeclinedException extends RuntimeException {

  private final String reason;

  public DepositDeclinedException(String reason) {
    super("The deposit was declined: " + reason);
    this.reason = reason;
  }

  /** The gateway's code, kept for the records; never shown to the tourist as it is. */
  public String reason() {
    return reason;
  }
}
