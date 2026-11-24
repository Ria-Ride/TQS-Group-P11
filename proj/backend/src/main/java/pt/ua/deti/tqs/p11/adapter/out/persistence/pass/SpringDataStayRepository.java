package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface SpringDataStayRepository extends JpaRepository<StayJpaEntity, UUID> {

  /** SELECT ... FOR UPDATE: the row stays locked until the transaction ends. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<StayJpaEntity> findWithLockByCode(String code);
}
