package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;

/**
 * A maintenance ticket (US10, US11), times in UTC. description and openedBy are null for a ticket
 * opened from a fault reading (automatic); technicianId is null until the ticket is assigned;
 * closedAt, workDone and timeToRepairMinutes are null until it is closed. breached (BR19) is for
 * now when the ticket is open, and for its closing time once it is closed.
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
    boolean automatic,
    Instant closedAt,
    String workDone,
    Long timeToRepairMinutes,
    boolean breached) {

  static TicketResponse from(MaintenanceTicket ticket, Instant now) {
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
        ticket.isAutomatic(),
        ticket.closedAt(),
        ticket.workDone(),
        ticket.timeToRepair().map(Duration::toMinutes).orElse(null),
        ticket.isBreachedAt(now));
  }
}
