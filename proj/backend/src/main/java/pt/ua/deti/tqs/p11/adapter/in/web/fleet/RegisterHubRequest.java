package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.FleetFields;
import pt.ua.deti.tqs.p11.application.fleet.RegisterHubCommand;

/** Body of POST /hubs. The domain checks the same rules; these give one error per field. */
public record RegisterHubRequest(
    @NotBlank @Pattern(regexp = FleetFields.CODE, message = FleetFields.CODE_MESSAGE) String code,
    @NotBlank @Size(max = FleetFields.NAME_MAX) String name,
    @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
    @NotNull @Min(1) Integer capacity) {

  RegisterHubCommand toCommand() {
    return new RegisterHubCommand(code, name, latitude, longitude, capacity);
  }
}
