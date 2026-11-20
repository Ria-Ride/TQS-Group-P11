package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;

/** VehicleStateChangeRepository backed by PostgreSQL. Not implemented yet (KAN-79). */
@Component
class VehicleStateChangePersistenceAdapter implements VehicleStateChangeRepository {

  @Override
  public void save(VehicleStateChange change) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  @Override
  public List<VehicleStateChange> findByVehicleId(UUID vehicleId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }
}
