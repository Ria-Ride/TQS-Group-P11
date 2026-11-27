package pt.ua.deti.tqs.p11.application.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A tourist asks to book a vehicle for a later period (US05).
 *
 * @param accountId the caller's account, from the access token, never from the request body
 * @param plannedStart when the ride starts: after now, and inside a valid Pass (BR4)
 * @param plannedEnd when the ride ends; the duration rules apply to the period (BR5)
 * @param paymentToken what the deposit is held on; in the simulated gateway, a test card
 */
public record ReserveCommand(
    UUID accountId, UUID vehicleId, Instant plannedStart, Instant plannedEnd, String paymentToken) {

  public ReserveCommand {
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(vehicleId, "vehicleId");
    Objects.requireNonNull(plannedStart, "plannedStart");
    Objects.requireNonNull(plannedEnd, "plannedEnd");
    Objects.requireNonNull(paymentToken, "paymentToken");
  }
}
