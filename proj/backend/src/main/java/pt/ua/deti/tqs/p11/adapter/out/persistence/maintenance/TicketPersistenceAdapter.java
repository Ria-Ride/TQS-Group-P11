package pt.ua.deti.tqs.p11.adapter.out.persistence.maintenance;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.maintenance.FaultAlreadyCoveredException;
import pt.ua.deti.tqs.p11.application.maintenance.TicketRepository;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;

/** TicketRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class TicketPersistenceAdapter implements TicketRepository {

  /** Name of the partial unique index in V9__create_maintenance_tickets.sql (BR20). */
  static final String OPEN_FAULT_UNIQUE_INDEX = "uk_maintenance_tickets_open_fault";

  /** The open states, taken from the domain so the rule is written once in Java (BR20). */
  private static final List<TicketState> OPEN_STATES =
      Arrays.stream(TicketState.values()).filter(TicketState::isOpen).toList();

  private final SpringDataTicketRepository repository;

  TicketPersistenceAdapter(SpringDataTicketRepository repository) {
    this.repository = repository;
  }

  @Override
  public MaintenanceTicket save(MaintenanceTicket ticket) {
    try {
      // Flush now, so a violation of the unique index surfaces here and not at a later commit.
      return repository.saveAndFlush(TicketJpaEntity.from(ticket)).toDomain();
    } catch (DataIntegrityViolationException e) {
      String cause = e.getMostSpecificCause().getMessage();
      if (cause != null && cause.contains(OPEN_FAULT_UNIQUE_INDEX)) {
        throw new FaultAlreadyCoveredException(ticket.vehicleId(), ticket.faultCode());
      }
      throw e;
    }
  }

  @Override
  public Optional<MaintenanceTicket> findById(UUID id) {
    return repository.findById(id).map(TicketJpaEntity::toDomain);
  }

  @Override
  public Optional<MaintenanceTicket> findOpenFor(UUID vehicleId, FaultCode faultCode) {
    return repository
        .findFirstByVehicleIdAndFaultCodeAndStateIn(vehicleId, faultCode.value(), OPEN_STATES)
        .map(TicketJpaEntity::toDomain);
  }

  @Override
  public List<MaintenanceTicket> findOpen() {
    return repository.findByStateInOrderBySlaDeadlineAsc(OPEN_STATES).stream()
        .map(TicketJpaEntity::toDomain)
        .toList();
  }

  @Override
  public List<MaintenanceTicket> findOpenAssignedTo(UUID technicianId) {
    throw new UnsupportedOperationException("US11");
  }

  @Override
  public boolean existsOpenFor(UUID vehicleId) {
    return repository.existsByVehicleIdAndStateIn(vehicleId, OPEN_STATES);
  }
}
