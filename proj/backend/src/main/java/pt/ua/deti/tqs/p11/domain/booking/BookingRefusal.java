package pt.ua.deti.tqs.p11.domain.booking;

/**
 * Why a booking cannot be created: one reason per business rule, so the API can tell the tourist
 * what to change. Checked in this order; the first that fails is reported.
 */
public enum BookingRefusal {
  /** BR5: the planned duration is not above 0 or is above maxBookingDuration. */
  DURATION_NOT_ALLOWED,
  /** BR2: the vehicle is not Available. */
  VEHICLE_NOT_AVAILABLE,
  /** BR1: no battery reading, below the floor, or not enough for the planned ride. */
  NOT_ENOUGH_BATTERY,
  /** BR4: no Pass of the tourist is valid when the booking starts. */
  NO_VALID_PASS,
  /** BR4: a Pass is valid, but none allows the vehicle's type. */
  PASS_DOES_NOT_ALLOW_TYPE,
  /** BR4: a Pass allows the type, but none is still valid when the ride ends. */
  RIDE_ENDS_AFTER_PASS,
  /** BR6: the tourist has a Reserved booking in that period, or is riding. */
  OVERLAPPING_BOOKING
}
