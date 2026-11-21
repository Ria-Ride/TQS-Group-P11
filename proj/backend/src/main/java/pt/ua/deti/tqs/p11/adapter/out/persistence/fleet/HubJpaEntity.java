package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;

/** JPA mapping of the hubs table. Kept out of the domain (no jakarta there). */
@Entity
@Table(name = "hubs")
class HubJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private double latitude;

  @Column(nullable = false)
  private double longitude;

  @Column(nullable = false)
  private int capacity;

  /** For JPA only. */
  protected HubJpaEntity() {}

  static HubJpaEntity from(Hub hub) {
    HubJpaEntity entity = new HubJpaEntity();
    entity.id = hub.id();
    entity.code = hub.code().value();
    entity.name = hub.name();
    entity.latitude = hub.position().latitude();
    entity.longitude = hub.position().longitude();
    entity.capacity = hub.capacity();
    return entity;
  }

  Hub toDomain() {
    return new Hub(id, FleetCode.of(code), name, new GeoPosition(latitude, longitude), capacity);
  }
}
