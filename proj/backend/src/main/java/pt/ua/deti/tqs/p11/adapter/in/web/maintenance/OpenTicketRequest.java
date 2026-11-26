package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MaintenanceFields;
import pt.ua.deti.tqs.p11.application.maintenance.OpenTicketCommand;

/** Body of POST /tickets. The operator is the caller, from the access token, never the body. */
record OpenTicketRequest(
    @Schema(description = "Id of the vehicle, from GET /vehicles") @NotNull UUID vehicleId,
    @Schema(description = "Code of the fault, UPPER_SNAKE_CASE", example = "BROKEN_BASKET")
        @NotBlank
        @Pattern(
            regexp = MaintenanceFields.FAULT_CODE,
            message = MaintenanceFields.FAULT_CODE_MESSAGE)
        String faultCode,
    @Schema(description = "What the operator saw", example = "Front basket cracked")
        @NotBlank
        @Size(max = MaintenanceFields.DESCRIPTION_MAX)
        String description) {

  OpenTicketCommand toCommand(UUID operatorId) {
    return new OpenTicketCommand(vehicleId, faultCode, description, operatorId);
  }
}
