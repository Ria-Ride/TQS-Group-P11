package pt.ua.deti.tqs.p11.domain.pass;

import java.time.LocalDate;

/** BR9: the stay code is used before check-in or after check-out. */
public class StayCodeNotValidTodayException extends RuntimeException {

  public StayCodeNotValidTodayException(
      String code, LocalDate today, LocalDate checkIn, LocalDate checkOut) {
    super(
        "Stay code %s is valid from %s to %s, not on %s".formatted(code, checkIn, checkOut, today));
  }
}
