package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPassTypeRepository extends JpaRepository<PassTypeJpaEntity, UUID> {

  boolean existsByCode(String code);
}
