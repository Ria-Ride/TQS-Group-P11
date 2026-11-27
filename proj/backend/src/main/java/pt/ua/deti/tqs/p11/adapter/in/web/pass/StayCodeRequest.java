package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import pt.ua.deti.tqs.p11.adapter.in.web.InvalidFieldException;

/** Body of POST /stay-codes (US14, BR9). The platform makes the code. */
record StayCodeRequest(
    @Schema(description = "Id of the partner hotel, from GET /partner-accommodations") @NotNull
        UUID accommodationId,
    @Schema(
            description = "First day the code is valid (the hotel's calendar day)",
            example = "2026-12-01")
        @NotNull
        LocalDate checkIn,
    @Schema(description = "Last day the code is valid, not before check-in", example = "2026-12-05")
        @NotNull
        LocalDate checkOut) {

  /** Check-out on the day of check-in is a one-day stay; before it is a mistake. */
  void checkDates() {
    if (checkOut.isBefore(checkIn)) {
      throw new InvalidFieldException("checkOut", "must not be before the check-in");
    }
  }
}
