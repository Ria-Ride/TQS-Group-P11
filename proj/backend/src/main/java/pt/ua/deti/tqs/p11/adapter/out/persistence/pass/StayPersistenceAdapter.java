package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import java.util.Optional;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.pass.StayRepository;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/** StayRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class StayPersistenceAdapter implements StayRepository {

  private final SpringDataStayRepository repository;

  StayPersistenceAdapter(SpringDataStayRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Stay> findByCodeForUpdate(String code) {
    return repository.findWithLockByCode(code).map(StayJpaEntity::toDomain);
  }

  @Override
  public Optional<Stay> findByCode(String code) {
    return repository.findByCode(code).map(StayJpaEntity::toDomain);
  }

  /**
   * Updates the linked account of the stored row, locked by findByCodeForUpdate in the same
   * transaction. The rest of the stay is never written from here.
   */
  @Override
  public void save(Stay stay) {
    StayJpaEntity row =
        repository
            .findById(stay.id())
            .orElseThrow(() -> new IllegalStateException("No stay with id " + stay.id()));
    row.linkTo(stay.redeemedBy().orElse(null));
    repository.saveAndFlush(row);
  }
}
