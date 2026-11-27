package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MaintenanceFields;

/** Body of POST /tickets/{id}/close. The technician is the caller, from the access token. */
record CloseTicketRequest(
    @Schema(description = "What the technician did", example = "Replaced brake pads")
        @NotBlank
        @Size(max = MaintenanceFields.WORK_DONE_MAX)
        String workDone) {}
