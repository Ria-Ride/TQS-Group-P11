package pt.ua.deti.tqs.p11.adapter.out.persistence.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

  boolean existsByEmail(String email);

  Optional<UserJpaEntity> findByEmail(String email);
}
