package pt.ua.deti.tqs.p11.application.booking;

import java.util.Objects;
import java.util.UUID;
import pt.ua.deti.tqs.p11.application.maintenance.FaultBookingCancellation;

/**
 * BR18: a Reserved vehicle reported a fault, so its reservation is cancelled with reason "vehicle
 * fault" and its whole deposit given back. Called by maintenance in the transaction of the fault
 * reading, with the vehicle already locked and in Maintenance, so the vehicle is left as it is.
 *
 * <p>Kept apart from ManageBookingUseCase on purpose: it needs neither the fleet nor the vehicle
 * commands, and the vehicle commands depend (through the MQTT connection) on the telemetry that
 * calls this, so one class with both would be a dependency cycle.
 */
public class FaultReservationCancellation implements FaultBookingCancellation {

  /** BR18: the reason kept on a reservation cancelled by a fault of its vehicle. */
  public static final String VEHICLE_FAULT = "vehicle fault";

  private final BookingRepository bookings;
  private final DepositReturn deposits;

  public FaultReservationCancellation(BookingRepository bookings, DepositReturn deposits) {
    this.bookings = Objects.requireNonNull(bookings, "bookings");
    this.deposits = Objects.requireNonNull(deposits, "deposits");
  }

  @Override
  public void cancelReservedBookingOf(UUID vehicleId) {
    bookings
        .findReservedOfVehicle(vehicleId)
        .ifPresent(reserved -> deposits.giveBack(bookings.save(reserved.cancel(VEHICLE_FAULT))));
  }
}
