package pt.ua.deti.tqs.p11.domain.maintenance;

import java.util.UUID;

/** BR20: only the technician the ticket is assigned to can start or close it. */
public class NotTheAssignedTechnicianException extends RuntimeException {

  private final UUID ticketId;
  private final UUID userId;

  public NotTheAssignedTechnicianException(UUID ticketId, UUID userId) {
    super("Ticket %s is not assigned to account %s".formatted(ticketId, userId));
    this.ticketId = ticketId;
    this.userId = userId;
  }

  public UUID ticketId() {
    return ticketId;
  }

  public UUID userId() {
    return userId;
  }
}
