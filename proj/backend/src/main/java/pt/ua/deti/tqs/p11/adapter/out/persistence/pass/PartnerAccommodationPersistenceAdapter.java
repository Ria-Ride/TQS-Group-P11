package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.pass.PartnerAccommodationRepository;
import pt.ua.deti.tqs.p11.domain.pass.PartnerAccommodation;

/** PartnerAccommodationRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class PartnerAccommodationPersistenceAdapter implements PartnerAccommodationRepository {

  private static final Sort BY_NAME = Sort.by("name");

  private final SpringDataPartnerAccommodationRepository repository;

  PartnerAccommodationPersistenceAdapter(SpringDataPartnerAccommodationRepository repository) {
    this.repository = repository;
  }

  @Override
  public PartnerAccommodation save(PartnerAccommodation accommodation) {
    // Flush now, so a unique violation of the name surfaces here and not at some later commit.
    return repository.saveAndFlush(PartnerAccommodationJpaEntity.from(accommodation)).toDomain();
  }

  @Override
  public Optional<PartnerAccommodation> findById(UUID id) {
    return repository.findById(id).map(PartnerAccommodationJpaEntity::toDomain);
  }

  @Override
  public boolean existsByName(String name) {
    return repository.existsByName(name);
  }

  @Override
  public List<PartnerAccommodation> findAll() {
    return repository.findAll(BY_NAME).stream()
        .map(PartnerAccommodationJpaEntity::toDomain)
        .toList();
  }
}
