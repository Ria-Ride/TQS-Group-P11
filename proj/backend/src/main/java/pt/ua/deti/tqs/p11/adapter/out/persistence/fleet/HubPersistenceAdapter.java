package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.fleet.HubCodeAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;

/** HubRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class HubPersistenceAdapter implements HubRepository {

  /** Name of the unique constraint in V3__create_fleet.sql. */
  static final String CODE_UNIQUE_CONSTRAINT = "uk_hubs_code";

  private final SpringDataHubRepository repository;

  HubPersistenceAdapter(SpringDataHubRepository repository) {
    this.repository = repository;
  }

  @Override
  public Hub save(Hub hub) {
    try {
      // Flush now, so a unique violation surfaces here and not at some later commit.
      return repository.saveAndFlush(HubJpaEntity.from(hub)).toDomain();
    } catch (DataIntegrityViolationException e) {
      if (ConstraintViolations.isViolationOf(e, CODE_UNIQUE_CONSTRAINT)) {
        throw new HubCodeAlreadyInUseException(hub.code());
      }
      throw e;
    }
  }

  @Override
  public Optional<Hub> findById(UUID id) {
    return repository.findById(id).map(HubJpaEntity::toDomain);
  }

  @Override
  public Optional<Hub> findByIdForUpdate(UUID id) {
    return repository.findWithLockById(id).map(HubJpaEntity::toDomain);
  }

  @Override
  public boolean existsByCode(FleetCode code) {
    return repository.existsByCode(code.value());
  }

  @Override
  public List<Hub> findAll() {
    return repository.findAll(Sort.by("code")).stream().map(HubJpaEntity::toDomain).toList();
  }
}
