package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;

/** HubRepository backed by PostgreSQL. Not implemented yet (KAN-79). */
@Component
class HubPersistenceAdapter implements HubRepository {

  @Override
  public Hub save(Hub hub) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  @Override
  public Optional<Hub> findById(UUID id) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  @Override
  public Optional<Hub> findByIdForUpdate(UUID id) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  @Override
  public boolean existsByCode(FleetCode code) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  @Override
  public List<Hub> findAll() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }
}
