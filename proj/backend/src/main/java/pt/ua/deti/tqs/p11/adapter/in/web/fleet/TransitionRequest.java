package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import pt.ua.deti.tqs.p11.adapter.in.web.InvalidFieldException;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleAction;

/**
 * Body of POST /vehicles/{id}/transitions: what the operator does to the vehicle. hubId is the
 * destination of CONFIRM_ARRIVAL, and only of that action.
 */
public record TransitionRequest(@NotNull VehicleAction action, UUID hubId) {

  /** Throws if hubId is missing for an arrival, or given for any other action. */
  void checkHubId() {
    boolean arrival = action == VehicleAction.CONFIRM_ARRIVAL;
    if (arrival && hubId == null) {
      throw new InvalidFieldException("hubId", "is required to confirm an arrival");
    }
    if (!arrival && hubId != null) {
      throw new InvalidFieldException("hubId", "is only used to confirm an arrival");
    }
  }
}
