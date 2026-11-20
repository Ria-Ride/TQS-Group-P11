package pt.ua.deti.tqs.p11.application.fleet;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.application.TransactionRunner;

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

  public HubOccupancy register(RegisterHubCommand command) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-81)");
  }

  public HubOccupancy changeCapacity(UUID hubId, int capacity) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-81)");
  }

  public List<HubOccupancy> list() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-81)");
  }

  public HubOccupancy get(UUID hubId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-81)");
  }
}
