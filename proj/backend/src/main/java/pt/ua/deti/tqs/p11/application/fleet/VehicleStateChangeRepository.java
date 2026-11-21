package pt.ua.deti.tqs.p11.application.fleet;

import java.util.List;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;

/** Port: the vehicles' state history (BR16). Entries are only added, never changed. */
public interface VehicleStateChangeRepository {

  void save(VehicleStateChange change);

  /** The history of one vehicle, in the order it happened. Empty if it has none. */
  List<VehicleStateChange> findByVehicleId(UUID vehicleId);
}
