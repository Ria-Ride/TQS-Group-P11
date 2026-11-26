package pt.ua.deti.tqs.p11.application.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A tourist asks to start using a vehicle now (US04).
 *
 * @param accountId the caller's account, from the access token, never from the request body
 * @param plannedEnd when the tourist plans to end the ride; the duration rules apply to it (BR5)
 * @param paymentToken what the deposit is held on; in the simulated gateway, a test card
 */
public record StartUseCommand(
    UUID accountId, UUID vehicleId, Instant plannedEnd, String paymentToken) {

  public StartUseCommand {
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(vehicleId, "vehicleId");
    Objects.requireNonNull(plannedEnd, "plannedEnd");
    Objects.requireNonNull(paymentToken, "paymentToken");
  }
}
