package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCodeAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;

/** VehicleRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class VehiclePersistenceAdapter implements VehicleRepository {

  /** Name of the unique constraint in V3__create_fleet.sql. */
  static final String CODE_UNIQUE_CONSTRAINT = "uk_vehicles_code";

  private final SpringDataVehicleRepository repository;

  VehiclePersistenceAdapter(SpringDataVehicleRepository repository) {
    this.repository = repository;
  }

  @Override
  public Vehicle save(Vehicle vehicle) {
    try {
      // Flush now, so a unique violation surfaces here and not at some later commit.
      return repository.saveAndFlush(VehicleJpaEntity.from(vehicle)).toDomain();
    } catch (DataIntegrityViolationException e) {
      if (ConstraintViolations.isViolationOf(e, CODE_UNIQUE_CONSTRAINT)) {
        throw new VehicleCodeAlreadyInUseException(vehicle.code());
      }
      throw e;
    }
  }

  @Override
  public Optional<Vehicle> findById(UUID id) {
    return repository.findById(id).map(VehicleJpaEntity::toDomain);
  }

  @Override
  public Optional<Vehicle> findByIdForUpdate(UUID id) {
    return repository.findWithLockById(id).map(VehicleJpaEntity::toDomain);
  }

  @Override
  public boolean existsByCode(FleetCode code) {
    return repository.existsByCode(code.value());
  }

  @Override
  public int countAtHub(UUID hubId) {
    return Math.toIntExact(repository.countByHubId(hubId));
  }

  @Override
  public List<Vehicle> findAll() {
    return repository.findAll(Sort.by("code")).stream().map(VehicleJpaEntity::toDomain).toList();
  }
}
