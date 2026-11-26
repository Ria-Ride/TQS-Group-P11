package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of PATCH /tickets/{id}/assignee. */
record AssignTicketRequest(
    @Schema(description = "Id of a field technician, from GET /technicians") @NotNull
        UUID technicianId) {}
