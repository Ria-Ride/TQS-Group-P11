package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataVehicleRepository extends JpaRepository<VehicleJpaEntity, UUID> {

  boolean existsByCode(String code);

  long countByHubId(UUID hubId);
}
