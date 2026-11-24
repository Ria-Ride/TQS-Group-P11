package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.pass.PassRepository;
import pt.ua.deti.tqs.p11.domain.pass.Pass;

/** PassRepository backed by PostgreSQL through Spring Data JPA. Passes are only inserted. */
@Component
class PassPersistenceAdapter implements PassRepository {

  private final SpringDataPassRepository repository;

  PassPersistenceAdapter(SpringDataPassRepository repository) {
    this.repository = repository;
  }

  @Override
  public Pass save(Pass pass) {
    repository.saveAndFlush(PassJpaEntity.from(pass));
    return pass;
  }

  @Override
  public List<Pass> findByAccount(UUID accountId) {
    return repository.findByAccountIdOrderByValidFromDesc(accountId).stream()
        .map(PassJpaEntity::toDomain)
        .toList();
  }
}
