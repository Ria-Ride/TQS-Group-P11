package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** A vehicle; hubId is null while it is InUse, InTransit or Retired. */
public record VehicleResponse(
    UUID id, String code, VehicleType type, VehicleState state, UUID hubId) {

  static VehicleResponse from(Vehicle vehicle) {
    return new VehicleResponse(
        vehicle.id(), vehicle.code().value(), vehicle.type(), vehicle.state(), vehicle.hubId());
  }
}
