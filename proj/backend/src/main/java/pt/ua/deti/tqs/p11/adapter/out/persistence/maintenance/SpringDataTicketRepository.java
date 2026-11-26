package pt.ua.deti.tqs.p11.adapter.out.persistence.maintenance;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;

interface SpringDataTicketRepository extends JpaRepository<TicketJpaEntity, UUID> {

  /** At most one row matches when states are the open ones (uk_maintenance_tickets_open_fault). */
  Optional<TicketJpaEntity> findFirstByVehicleIdAndFaultCodeAndStateIn(
      UUID vehicleId, String faultCode, Collection<TicketState> states);

  boolean existsByVehicleIdAndStateIn(UUID vehicleId, Collection<TicketState> states);
}
