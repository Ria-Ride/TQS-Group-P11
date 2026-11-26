package pt.ua.deti.tqs.p11.adapter.out.persistence.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

  boolean existsByEmail(String email);

  Optional<UserJpaEntity> findByEmail(String email);

  /** SELECT ... FOR UPDATE: the row stays locked until the transaction ends. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<UserJpaEntity> findWithLockById(UUID id);
}
