package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.pass.StayRepository;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/** StayRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class StayPersistenceAdapter implements StayRepository {

  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Direction.DESC, "checkIn").and(Sort.by("code"));

  private final SpringDataStayRepository repository;
  private final SpringDataPartnerAccommodationRepository accommodations;

  StayPersistenceAdapter(
      SpringDataStayRepository repository,
      SpringDataPartnerAccommodationRepository accommodations) {
    this.repository = repository;
    this.accommodations = accommodations;
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

  @Override
  public boolean existsByCode(String code) {
    return repository.existsByCode(code.toUpperCase(Locale.ROOT));
  }

  @Override
  public void add(Stay stay) {
    PartnerAccommodationJpaEntity hotel =
        accommodations
            .findById(stay.accommodation().id())
            .orElseThrow(
                () -> new IllegalStateException("No hotel with id " + stay.accommodation().id()));
    repository.saveAndFlush(StayJpaEntity.from(stay, hotel));
  }

  @Override
  public List<Stay> findAll() {
    return repository.findAll(NEWEST_FIRST).stream().map(StayJpaEntity::toDomain).toList();
  }
}
