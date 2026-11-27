package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;

/**
 * A maintenance ticket (US10), times in UTC. description and openedBy are null for a ticket opened
 * from a fault reading (automatic); technicianId is null until the ticket is assigned.
 */
record TicketResponse(
    UUID id,
    UUID vehicleId,
    String faultCode,
    String description,
    TicketState state,
    Instant openedAt,
    Instant slaDeadline,
    UUID openedBy,
    UUID technicianId,
    boolean automatic) {

  static TicketResponse from(MaintenanceTicket ticket) {
    return new TicketResponse(
        ticket.id(),
        ticket.vehicleId(),
        ticket.faultCode().value(),
        ticket.description(),
        ticket.state(),
        ticket.openedAt(),
        ticket.slaDeadline(),
        ticket.openedByOperator().orElse(null),
        ticket.assignedTechnician().orElse(null),
        ticket.isAutomatic());
  }
}
