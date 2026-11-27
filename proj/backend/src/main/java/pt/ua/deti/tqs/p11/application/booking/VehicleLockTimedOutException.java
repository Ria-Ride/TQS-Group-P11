package pt.ua.deti.tqs.p11.application.booking;

/** US06: the vehicle gave no answer to the lock command in time, so the ride goes on. */
public class VehicleLockTimedOutException extends RuntimeException {

  public VehicleLockTimedOutException() {
    super("The vehicle did not answer the lock command in time");
  }
}
