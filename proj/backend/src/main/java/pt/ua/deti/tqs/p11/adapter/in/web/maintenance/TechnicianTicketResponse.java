package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.application.maintenance.TechnicianTicket;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;

/**
 * One ticket of a field technician's list (US11), times in UTC. hubCode and hubName are null when
 * the vehicle is at no hub; latitude and longitude are its last known position, null before the
 * first reading with one. breached is BR19 at the time of the request.
 */
record TechnicianTicketResponse(
    UUID id,
    UUID vehicleId,
    String vehicleCode,
    String faultCode,
    String description,
    TicketState state,
    Instant openedAt,
    Instant slaDeadline,
    String hubCode,
    String hubName,
    Double latitude,
    Double longitude,
    boolean breached) {

  static TechnicianTicketResponse from(TechnicianTicket entry) {
    MaintenanceTicket ticket = entry.ticket();
    GeoPosition position = entry.lastPosition();
    return new TechnicianTicketResponse(
        ticket.id(),
        ticket.vehicleId(),
        entry.vehicleCode(),
        ticket.faultCode().value(),
        ticket.description(),
        ticket.state(),
        ticket.openedAt(),
        ticket.slaDeadline(),
        entry.hubCode(),
        entry.hubName(),
        position == null ? null : position.latitude(),
        position == null ? null : position.longitude(),
        entry.breached());
  }
}
