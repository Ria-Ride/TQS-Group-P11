package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;

/** VehicleStateChangeRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class VehicleStateChangePersistenceAdapter implements VehicleStateChangeRepository {

  private final SpringDataVehicleStateChangeRepository repository;

  VehicleStateChangePersistenceAdapter(SpringDataVehicleStateChangeRepository repository) {
    this.repository = repository;
  }

  @Override
  public void save(VehicleStateChange change) {
    repository.save(VehicleStateChangeJpaEntity.from(change));
  }

  /** Ordered by the generated id, i.e. the order in which the changes were recorded. */
  @Override
  public List<VehicleStateChange> findByVehicleId(UUID vehicleId) {
    return repository.findByVehicleIdOrderById(vehicleId).stream()
        .map(VehicleStateChangeJpaEntity::toDomain)
        .toList();
  }
}
