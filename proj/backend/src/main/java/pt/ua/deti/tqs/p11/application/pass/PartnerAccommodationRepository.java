package pt.ua.deti.tqs.p11.application.pass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.pass.PartnerAccommodation;

/** Port: where the partner hotels are stored. */
public interface PartnerAccommodationRepository {

  PartnerAccommodation save(PartnerAccommodation accommodation);

  Optional<PartnerAccommodation> findById(UUID id);

  boolean existsByName(String name);

  /** Every hotel, ordered by name. */
  List<PartnerAccommodation> findAll();
}
