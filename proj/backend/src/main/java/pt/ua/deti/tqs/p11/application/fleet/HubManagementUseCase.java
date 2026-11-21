package pt.ua.deti.tqs.p11.application.fleet;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;

/**
 * What an operator does with hubs (US08): register, change the capacity, list. A capacity change
 * locks the hub, so it cannot race a vehicle taking a slot at the same hub (BR14, K3).
 */
public class HubManagementUseCase {

  private final HubRepository hubs;
  private final VehicleRepository vehicles;
  private final TransactionRunner transactions;
  private final Supplier<UUID> idGenerator;

  public HubManagementUseCase(
      HubRepository hubs,
      VehicleRepository vehicles,
      TransactionRunner transactions,
      Supplier<UUID> idGenerator) {
    this.hubs = Objects.requireNonNull(hubs, "hubs");
    this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
  }

  /** A new hub holds no vehicles. Its data is checked by the domain before anything is read. */
  public HubOccupancy register(RegisterHubCommand command) {
    FleetCode code = FleetCode.of(command.code());
    Hub hub =
        new Hub(
            idGenerator.get(),
            code,
            command.name(),
            new GeoPosition(command.latitude(), command.longitude()),
            command.capacity());
    if (hubs.existsByCode(code)) {
      throw new HubCodeAlreadyInUseException(code);
    }
    return new HubOccupancy(hubs.save(hub), 0);
  }

  /** BR14: refused below the vehicles held, counted while the hub is locked. */
  public HubOccupancy changeCapacity(UUID hubId, int capacity) {
    return transactions.inTransaction(() -> {
      Hub hub = hubs.findByIdForUpdate(hubId).orElseThrow(() -> new HubNotFoundException(hubId));
      int held = vehicles.countAtHub(hubId);
      return new HubOccupancy(hubs.save(hub.withCapacity(capacity, held)), held);
    });
  }

  /** One count per hub: fine for a fleet of a few dozen hubs. */
  public List<HubOccupancy> list() {
    return hubs.findAll().stream().map(this::withOccupancy).toList();
  }

  public HubOccupancy get(UUID hubId) {
    return withOccupancy(hubs.findById(hubId).orElseThrow(() -> new HubNotFoundException(hubId)));
  }

  private HubOccupancy withOccupancy(Hub hub) {
    return new HubOccupancy(hub, vehicles.countAtHub(hub.id()));
  }
}
