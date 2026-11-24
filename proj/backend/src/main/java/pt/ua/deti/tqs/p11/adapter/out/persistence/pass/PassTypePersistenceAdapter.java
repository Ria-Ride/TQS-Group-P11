package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.pass.PassTypeRepository;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/** PassTypeRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class PassTypePersistenceAdapter implements PassTypeRepository {

  private static final Sort SHORTEST_FIRST = Sort.by("validityDays", "code");

  private final SpringDataPassTypeRepository repository;

  PassTypePersistenceAdapter(SpringDataPassTypeRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<PassType> findById(UUID id) {
    return repository.findById(id).map(PassTypeJpaEntity::toDomain);
  }

  @Override
  public List<PassType> findAll() {
    return repository.findAll(SHORTEST_FIRST).stream().map(PassTypeJpaEntity::toDomain).toList();
  }
}
