package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * JPA mapping of the vehicles table. The hub is kept as its id, not as a relation: a vehicle and a
 * hub are loaded and saved separately.
 */
@Entity
@Table(name = "vehicles")
class VehicleJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VehicleType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VehicleState state;

  @Column(name = "hub_id")
  private UUID hubId;

  /** For JPA only. */
  protected VehicleJpaEntity() {}

  static VehicleJpaEntity from(Vehicle vehicle) {
    VehicleJpaEntity entity = new VehicleJpaEntity();
    entity.id = vehicle.id();
    entity.code = vehicle.code().value();
    entity.type = vehicle.type();
    entity.state = vehicle.state();
    entity.hubId = vehicle.hubId();
    return entity;
  }

  Vehicle toDomain() {
    return new Vehicle(id, FleetCode.of(code), type, state, hubId);
  }
}
