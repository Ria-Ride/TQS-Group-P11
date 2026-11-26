package pt.ua.deti.tqs.p11.domain.booking;

/** What can happen to a booking after it is created (Product Specs 3.3). */
public enum BookingAction {
  START,
  COMPLETE,
  CANCEL,
  EXPIRE
}
