package pt.ua.deti.tqs.p11.application.fleet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;

/** Port: where vehicles are stored. */
public interface VehicleRepository {

  /**
   * Inserts or updates the vehicle.
   *
   * @throws VehicleCodeAlreadyInUseException if another vehicle has the same code
   */
  Vehicle save(Vehicle vehicle);

  Optional<Vehicle> findById(UUID id);

  boolean existsByCode(FleetCode code);

  /**
   * BR14: how many vehicles the hub holds. Only vehicles physically at a hub have one, so this is
   * the count of vehicles whose current hub is this one.
   */
  int countAtHub(UUID hubId);

  /** Every vehicle, ordered by code. */
  List<Vehicle> findAll();
}
