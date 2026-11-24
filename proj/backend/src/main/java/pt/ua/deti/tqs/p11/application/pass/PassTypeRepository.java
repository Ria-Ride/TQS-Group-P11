package pt.ua.deti.tqs.p11.application.pass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/** Port: where pass types are stored. Seeded until US14 lets the client manager edit them. */
public interface PassTypeRepository {

  Optional<PassType> findById(UUID id);

  /** Every pass type, shortest validity first. */
  List<PassType> findAll();
}
