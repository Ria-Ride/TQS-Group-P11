package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.booking.EndUseUseCase;
import pt.ua.deti.tqs.p11.application.booking.ManageBookingUseCase;
import pt.ua.deti.tqs.p11.application.booking.Requester;
import pt.ua.deti.tqs.p11.application.booking.StartUseUseCase;
import pt.ua.deti.tqs.p11.domain.booking.Booking;

/**
 * Bookings (US04 to US06). Tourists (END_USER) book, start and cancel their own; an ADMIN may act
 * on any booking (SecurityConfiguration and the access matrix, BR23). A new booking is always the
 * caller's, so nobody books for someone else. The rules live in the use cases and the domain; this
 * class only maps HTTP to them.
 */
@RestController
@Tag(
    name = "Bookings",
    description = "Immediate use (US04), bookings for later (US05) and the end of a ride (US06)")
class BookingController {

  private static final String ADMIN = "ROLE_ADMIN";

  private final StartUseUseCase startUse;
  private final ManageBookingUseCase manage;
  private final EndUseUseCase endUse;

  BookingController(StartUseUseCase startUse, ManageBookingUseCase manage, EndUseUseCase endUse) {
    this.startUse = startUse;
    this.manage = manage;
    this.endUse = endUse;
  }

  @PostMapping("/bookings")
  @Operation(
      summary = "Start using an available vehicle now, or book it for later",
      description =
          "Checks the rules of use (BR1, BR2, BR4 to BR6) and holds the deposit of the vehicle's"
              + " type (BR11); only then the vehicle changes. Without plannedStart the vehicle goes"
              + " InUse, the booking is Active and the vehicle is asked to unlock (US04). With"
              + " plannedStart the vehicle and the booking are Reserved until the booking is"
              + " started in its window, cancelled or expires (US05). Exactly one of several"
              + " simultaneous requests for a vehicle succeeds (BR3).")
  @ApiResponse(responseCode = "201", description = "Created; the booking is Active or Reserved")
  @ApiResponse(
      responseCode = "400",
      description =
          "Invalid field: plannedStart not in the future; plannedEnd not after the start, above"
              + " the longest booking, or after the end of the Pass; or a missing field")
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
  ResponseEntity<BookingResponse> create(
      @Valid @RequestBody StartUseRequest request, Authentication caller) {
    UUID account = accountOf(caller);
    Booking booking =
        request.isForLater()
            ? startUse.reserve(request.toReserveCommand(account))
            : startUse.start(request.toCommand(account));
    return ResponseEntity.created(URI.create("/bookings/" + booking.id()))
        .body(BookingResponse.from(booking));
  }

  @GetMapping("/bookings")
  @Operation(summary = "The caller's bookings, latest planned start first")
  @ApiResponse(responseCode = "200", description = "The bookings; empty if none")
  List<BookingResponse> list(Authentication caller) {
    return manage.listOf(accountOf(caller)).stream().map(BookingResponse::from).toList();
  }

  @GetMapping("/bookings/{id}")
  @Operation(summary = "One of the caller's bookings")
  @ApiResponse(responseCode = "200", description = "The booking")
  @ApiResponse(responseCode = "403", description = "The booking is another account's (BR23)")
  @ApiResponse(responseCode = "404", description = "No such booking")
  BookingResponse get(@PathVariable UUID id, Authentication caller) {
    return BookingResponse.from(manage.get(id, requester(caller)));
  }

  @PostMapping("/bookings/{id}/start")
  @Operation(
      summary = "Start a Reserved booking at its hub",
      description =
          "BR7: from plannedStart up to startWindowEndsAt, both included. The booking becomes"
              + " Active, the vehicle InUse, and the vehicle is asked to unlock. The deposit stays"
              + " held until the ride ends.")
  @ApiResponse(responseCode = "200", description = "Started; the booking is Active")
  @ApiResponse(responseCode = "403", description = "The booking is another account's (BR23)")
  @ApiResponse(responseCode = "404", description = "No such booking")
  @ApiResponse(
      responseCode = "409",
      description = "booking: not Reserved, before its planned start, or after its start window")
  BookingResponse start(@PathVariable UUID id, Authentication caller) {
    return BookingResponse.from(manage.start(id, requester(caller)));
  }

  @PostMapping("/bookings/{id}/cancel")
  @Operation(
      summary = "Cancel a Reserved booking",
      description =
          "BR13: the booking is Cancelled, the vehicle Available again and the whole deposit is"
              + " given back. Only a Reserved booking can be cancelled.")
  @ApiResponse(responseCode = "200", description = "Cancelled")
  @ApiResponse(responseCode = "403", description = "The booking is another account's (BR23)")
  @ApiResponse(responseCode = "404", description = "No such booking")
  @ApiResponse(responseCode = "409", description = "booking: not Reserved")
  BookingResponse cancel(@PathVariable UUID id, Authentication caller) {
    return BookingResponse.from(manage.cancel(id, requester(caller)));
  }

  @PostMapping("/bookings/{id}/end")
  @Operation(
      summary = "End an Active ride at a hub",
      description =
          "BR14: the hub needs a free slot; it may be another than the start hub. The vehicle is"
              + " asked to lock and its answer is waited for; then the vehicle is Available at the"
              + " hub, the booking Completed, and the deposit settled: the overtime charge (BR12) is"
              + " captured, at most the whole deposit, and the rest released (BR13). A full hub"
              + " refuses, keeps the first refusal (fullHubAttemptAt) and lists nearbyHubs with"
              + " room; ending at one of them within the repositioning window charges overtime"
              + " only until that refusal (BR15).")
  @ApiResponse(responseCode = "200", description = "Ended, with the charge and the settlement")
  @ApiResponse(responseCode = "400", description = "hubId missing")
  @ApiResponse(responseCode = "403", description = "The booking is another account's (BR23)")
  @ApiResponse(responseCode = "404", description = "No such booking or hub")
  @ApiResponse(
      responseCode = "409",
      description =
          "booking: not Active; hubId: the hub is full (nearbyHubs lists hubs with room);"
              + " vehicleId: the vehicle could not be locked. The ride goes on")
  @ApiResponse(
      responseCode = "504",
      description = "vehicleId: the vehicle did not answer the lock in time. The ride goes on")
  BookingResponse end(
      @PathVariable UUID id, @Valid @RequestBody EndUseRequest request, Authentication caller) {
    return BookingResponse.from(endUse.end(id, request.hubId(), requester(caller)));
  }

  /** The access token's subject is the account id (ActiveAccountAuthenticationConverter). */
  private static UUID accountOf(Authentication caller) {
    return UUID.fromString(caller.getName());
  }

  /** BR23: an ADMIN may act on any booking (access matrix); anyone else on their own. */
  private static Requester requester(Authentication caller) {
    boolean admin =
        caller.getAuthorities().stream().anyMatch(role -> ADMIN.equals(role.getAuthority()));
    return new Requester(accountOf(caller), admin);
  }
}
