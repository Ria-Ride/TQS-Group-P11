package pt.ua.deti.tqs.p11.adapter.out.persistence.maintenance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.maintenance.TicketRepository;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;

/** TicketRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class TicketPersistenceAdapter implements TicketRepository {

  @Override
  public MaintenanceTicket save(MaintenanceTicket ticket) {
    throw new UnsupportedOperationException("US10");
  }

  @Override
  public Optional<MaintenanceTicket> findById(UUID id) {
    throw new UnsupportedOperationException("US10");
  }

  @Override
  public Optional<MaintenanceTicket> findOpenFor(UUID vehicleId, FaultCode faultCode) {
    throw new UnsupportedOperationException("US10");
  }

  @Override
  public boolean existsOpenFor(UUID vehicleId) {
    throw new UnsupportedOperationException("US10");
  }
}
