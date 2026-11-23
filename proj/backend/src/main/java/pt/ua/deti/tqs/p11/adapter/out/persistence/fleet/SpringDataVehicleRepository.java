package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface SpringDataVehicleRepository extends JpaRepository<VehicleJpaEntity, UUID> {

  boolean existsByCode(String code);

  long countByHubId(UUID hubId);

  List<VehicleJpaEntity> findByHubIdOrderByCodeAsc(UUID hubId);

  /** SELECT ... FOR UPDATE: the row stays locked until the transaction ends. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<VehicleJpaEntity> findWithLockById(UUID id);
}
