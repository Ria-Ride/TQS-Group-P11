package pt.ua.deti.tqs.p11.application.maintenance;

import java.util.UUID;

/**
 * Port: cancels the Reserved booking of a vehicle that reported a fault, with reason "vehicle
 * fault", and releases its deposit (BR18). Implemented by bookings (US05); until then no vehicle
 * can be Reserved, so there is nothing to cancel.
 */
public interface FaultBookingCancellation {

  void cancelReservedBookingOf(UUID vehicleId);
}
