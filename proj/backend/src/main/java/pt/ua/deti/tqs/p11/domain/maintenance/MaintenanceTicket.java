package pt.ua.deti.tqs.p11.domain.maintenance;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * A repair job for one vehicle and one fault (US10). Immutable: every change returns a new ticket,
 * and the constructor checks the rules, so an invalid ticket cannot exist. description is null for
 * a ticket opened from a fault reading; openedBy is the operator of a manual ticket, null for one
 * opened from a fault reading; technicianId is null until the ticket is assigned.
 */
public record MaintenanceTicket(
    UUID id,
    UUID vehicleId,
    FaultCode faultCode,
    String description,
    TicketState state,
    Instant openedAt,
    Instant slaDeadline,
    UUID openedBy,
    UUID technicianId) {

  /** BR18, BR19: a ticket opened from a fault reading, due openedAt + sla. */
  public static MaintenanceTicket openFromFault(
      UUID id, UUID vehicleId, FaultCode faultCode, Instant openedAt, Duration sla) {
    throw new UnsupportedOperationException("US10");
  }

  /** BR19: a ticket opened by an operator for a fault the simulator cannot detect. */
  public static MaintenanceTicket openManually(
      UUID id,
      UUID vehicleId,
      FaultCode faultCode,
      String description,
      UUID operatorId,
      Instant openedAt,
      Duration sla) {
    throw new UnsupportedOperationException("US10");
  }

  public Optional<UUID> assignedTechnician() {
    throw new UnsupportedOperationException("US10");
  }

  public Optional<UUID> openedByOperator() {
    throw new UnsupportedOperationException("US10");
  }

  /** Whether the ticket was opened from a fault reading rather than by an operator. */
  public boolean isAutomatic() {
    throw new UnsupportedOperationException("US10");
  }

  /** BR20: only an active OPERATOR who is a field technician can be assigned. */
  public MaintenanceTicket assignTo(User technician) {
    throw new UnsupportedOperationException("US10");
  }
}
