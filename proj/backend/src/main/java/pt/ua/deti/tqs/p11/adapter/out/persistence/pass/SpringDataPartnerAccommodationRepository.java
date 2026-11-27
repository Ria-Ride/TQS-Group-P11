package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPartnerAccommodationRepository
    extends JpaRepository<PartnerAccommodationJpaEntity, UUID> {

  boolean existsByName(String name);
}
