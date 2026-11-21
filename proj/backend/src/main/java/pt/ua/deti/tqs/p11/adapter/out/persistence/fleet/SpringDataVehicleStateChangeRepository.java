package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataVehicleStateChangeRepository
    extends JpaRepository<VehicleStateChangeJpaEntity, Long> {

  List<VehicleStateChangeJpaEntity> findByVehicleIdOrderById(UUID vehicleId);
}
