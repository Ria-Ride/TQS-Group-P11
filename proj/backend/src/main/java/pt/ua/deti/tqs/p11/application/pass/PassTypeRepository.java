package pt.ua.deti.tqs.p11.application.pass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/** Port: where pass types are stored. Seeded, and edited by the client manager (US14). */
public interface PassTypeRepository {

  Optional<PassType> findById(UUID id);

  boolean existsByCode(String code);

  /** Inserts or updates the pass type. Passes already bought are not touched. */
  PassType save(PassType passType);

  /** Every pass type, shortest validity first. */
  List<PassType> findAll();
}
