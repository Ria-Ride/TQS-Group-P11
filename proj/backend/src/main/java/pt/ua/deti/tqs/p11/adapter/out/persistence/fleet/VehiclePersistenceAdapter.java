package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;

/** VehicleRepository backed by PostgreSQL. Not implemented yet (KAN-79). */
@Component
class VehiclePersistenceAdapter implements VehicleRepository {

  @Override
  public Vehicle save(Vehicle vehicle) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  @Override
  public Optional<Vehicle> findById(UUID id) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  @Override
  public boolean existsByCode(FleetCode code) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  @Override
  public int countAtHub(UUID hubId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  @Override
  public List<Vehicle> findAll() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }
}
