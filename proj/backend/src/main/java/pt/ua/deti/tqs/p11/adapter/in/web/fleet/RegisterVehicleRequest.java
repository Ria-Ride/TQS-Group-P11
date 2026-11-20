package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.FleetFields;
import pt.ua.deti.tqs.p11.application.fleet.RegisterVehicleCommand;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** Body of POST /vehicles: the vehicle's code, its type and the hub where it is registered. */
public record RegisterVehicleRequest(
    @NotBlank @Pattern(regexp = FleetFields.CODE, message = FleetFields.CODE_MESSAGE) String code,
    @NotNull VehicleType type,
    @NotNull UUID hubId) {

  RegisterVehicleCommand toCommand() {
    return new RegisterVehicleCommand(code, type, hubId);
  }
}
