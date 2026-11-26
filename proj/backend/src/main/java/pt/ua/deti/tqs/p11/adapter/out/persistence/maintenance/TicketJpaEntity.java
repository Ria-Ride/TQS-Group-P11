package pt.ua.deti.tqs.p11.adapter.out.persistence.maintenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;

/**
 * JPA mapping of the maintenance_tickets table. The vehicle, the operator and the technician are
 * kept as ids, not as relations: each is loaded and saved on its own.
 */
@Entity
@Table(name = "maintenance_tickets")
class TicketJpaEntity {

  @Id private UUID id;

  @Column(name = "vehicle_id", nullable = false)
  private UUID vehicleId;

  @Column(name = "fault_code", nullable = false, length = 64)
  private String faultCode;

  @Column(length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TicketState state;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "sla_deadline", nullable = false)
  private Instant slaDeadline;

  @Column(name = "opened_by")
  private UUID openedBy;

  @Column(name = "technician_id")
  private UUID technicianId;

  /** For JPA only. */
  protected TicketJpaEntity() {}

  static TicketJpaEntity from(MaintenanceTicket ticket) {
    TicketJpaEntity entity = new TicketJpaEntity();
    entity.id = ticket.id();
    entity.vehicleId = ticket.vehicleId();
    entity.faultCode = ticket.faultCode().value();
    entity.description = ticket.description();
    entity.state = ticket.state();
    entity.openedAt = ticket.openedAt();
    entity.slaDeadline = ticket.slaDeadline();
    entity.openedBy = ticket.openedByOperator().orElse(null);
    entity.technicianId = ticket.assignedTechnician().orElse(null);
    return entity;
  }

  MaintenanceTicket toDomain() {
    return new MaintenanceTicket(
        id,
        vehicleId,
        FaultCode.of(faultCode),
        description,
        state,
        openedAt,
        slaDeadline,
        openedBy,
        technicianId);
  }
}
