package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/** JPA mapping of pass_types and the vehicle types each allows. Read only until US14. */
@Entity
@Table(name = "pass_types")
class PassTypeJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "validity_days", nullable = false)
  private int validityDays;

  @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal basePrice;

  // At most two vehicle types, always needed with the pass type: load them together.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "pass_type_vehicle_types",
      joinColumns = @JoinColumn(name = "pass_type_id"))
  @Column(name = "vehicle_type", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private Set<VehicleType> vehicleTypes = new HashSet<>();

  /** For JPA only. */
  protected PassTypeJpaEntity() {}

  static PassTypeJpaEntity from(PassType passType) {
    PassTypeJpaEntity entity = new PassTypeJpaEntity();
    entity.id = passType.id();
    entity.code = passType.code();
    entity.name = passType.name();
    entity.validityDays = passType.validityDays();
    entity.basePrice = passType.basePrice().amount();
    entity.vehicleTypes = new HashSet<>(passType.vehicleTypes());
    return entity;
  }

  PassType toDomain() {
    return new PassType(
        id, code, name, validityDays, new Money(basePrice), Set.copyOf(vehicleTypes));
  }
}
