package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPassRepository extends JpaRepository<PassJpaEntity, UUID> {

  List<PassJpaEntity> findByAccountIdOrderByValidFromDesc(UUID accountId);
}
