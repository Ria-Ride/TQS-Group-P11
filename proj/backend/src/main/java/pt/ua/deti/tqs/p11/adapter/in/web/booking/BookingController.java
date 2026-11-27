package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.booking.StartUseUseCase;
import pt.ua.deti.tqs.p11.domain.booking.Booking;

/**
 * Bookings (US04 to US06). Tourists (END_USER) start their own rides (SecurityConfiguration and the
 * access matrix, BR23); the account is always the caller's, so nobody books for someone else. The
 * rules live in the use case and the domain; this class only maps HTTP to them.
 */
@RestController
@Tag(name = "Bookings", description = "Immediate use of a vehicle (US04)")
class BookingController {

  private final StartUseUseCase startUse;

  BookingController(StartUseUseCase startUse) {
    this.startUse = startUse;
  }

  @PostMapping("/bookings")
  @Operation(
      summary = "Start using an available vehicle now",
      description =
          "Checks the rules of use (BR1, BR2, BR4 to BR6), holds the deposit of the vehicle's type"
              + " (BR11) and, only then, puts the vehicle InUse and creates an Active booking."
              + " Exactly one of several simultaneous requests for a vehicle succeeds (BR3). The"
              + " vehicle is then asked to unlock.")
  @ApiResponse(responseCode = "201", description = "Started; the booking is Active")
  @ApiResponse(
      responseCode = "400",
      description =
          "Invalid field: plannedEnd not after now, above the longest booking, or after the end of"
              + " the Pass; or a missing field")
  @ApiResponse(responseCode = "402", description = "The deposit was declined; no booking")
  @ApiResponse(responseCode = "404", description = "No such vehicle")
  @ApiResponse(
      responseCode = "409",
      description =
          "vehicleId: not available or not enough battery; pass: no valid Pass or not for this"
              + " vehicle type; booking: another booking in that period. No deposit is kept")
  @ApiResponse(
      responseCode = "504",
      description = "The deposit got no answer in time; no booking. Safe to try again")
  ResponseEntity<BookingResponse> start(
      @Valid @RequestBody StartUseRequest request, Authentication caller) {
    Booking booking = startUse.start(request.toCommand(accountOf(caller)));
    return ResponseEntity.created(URI.create("/bookings/" + booking.id()))
        .body(BookingResponse.from(booking));
  }

  /** The access token's subject is the account id (ActiveAccountAuthenticationConverter). */
  private static UUID accountOf(Authentication caller) {
    return UUID.fromString(caller.getName());
  }
}
