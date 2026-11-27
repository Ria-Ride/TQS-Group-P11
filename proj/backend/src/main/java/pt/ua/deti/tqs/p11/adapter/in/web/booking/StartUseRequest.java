package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.application.booking.ReserveCommand;
import pt.ua.deti.tqs.p11.application.booking.StartUseCommand;

/**
 * Body of POST /bookings. Without plannedStart the ride starts now (US04); with it, the vehicle is
 * reserved for that later period (US05). The account is the caller's, never the body.
 */
record StartUseRequest(
    @Schema(description = "Id of an Available vehicle, from the vehicle search") @NotNull
        UUID vehicleId,
    @Schema(
            description =
                "US05: when a booking for later starts, in UTC; after now and inside a valid Pass."
                    + " Leave it out to start using the vehicle now",
            example = "2026-11-26T14:00:00Z",
            nullable = true)
        Instant plannedStart,
    @Schema(
            description =
                "When the tourist plans to end the ride, in UTC. After the start and at most the"
                    + " longest booking allowed (4 h by default) after it, and not after the end of"
                    + " the Pass",
            example = "2026-11-26T15:00:00Z")
        @NotNull
        Instant plannedEnd,
    @Schema(
            description =
                "What the deposit is held on. In the simulated gateway, a test card: tok_approve,"
                    + " tok_decline or tok_timeout",
            example = "tok_approve")
        @NotBlank
        @Size(max = 64)
        String paymentToken) {

  boolean isForLater() {
    return plannedStart != null;
  }

  StartUseCommand toCommand(UUID accountId) {
    return new StartUseCommand(accountId, vehicleId, plannedEnd, paymentToken);
  }

  ReserveCommand toReserveCommand(UUID accountId) {
    return new ReserveCommand(accountId, vehicleId, plannedStart, plannedEnd, paymentToken);
  }
}
