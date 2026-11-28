package pt.ua.deti.tqs.p11.application.booking;

/**
 * US06: the vehicle answered the lock command but did not lock (e.g. NOT_AT_HUB: it is between
 * hubs), so the ride goes on.
 */
public class VehicleNotLockedException extends RuntimeException {

  private final String reason;

  public VehicleNotLockedException(String reason) {
    super("The vehicle could not be locked: " + reason);
    this.reason = reason;
  }

  /** The simulator contract's code, for logs; never shown to the tourist as it is. */
  public String reason() {
    return reason;
  }
}
