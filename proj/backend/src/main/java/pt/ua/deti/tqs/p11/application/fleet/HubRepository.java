package pt.ua.deti.tqs.p11.application.fleet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;

/** Port: where hubs are stored. */
public interface HubRepository {

  /**
   * Inserts or updates the hub.
   *
   * @throws HubCodeAlreadyInUseException if another hub has the same code
   */
  Hub save(Hub hub);

  Optional<Hub> findById(UUID id);

  /**
   * Finds the hub and locks it until the current transaction ends, so that two operations that
   * add a vehicle to the same hub run one after the other and cannot both take its last free slot
   * (BR14, K3). Must be called inside a transaction.
   */
  Optional<Hub> findByIdForUpdate(UUID id);

  boolean existsByCode(FleetCode code);

  /** Every hub, ordered by code. */
  List<Hub> findAll();
}
