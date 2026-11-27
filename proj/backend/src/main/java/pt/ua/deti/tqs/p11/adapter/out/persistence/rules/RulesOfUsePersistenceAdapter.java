package pt.ua.deti.tqs.p11.adapter.out.persistence.rules;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.rules.RulesOfUseRepository;
import pt.ua.deti.tqs.p11.application.rules.RulesVersionConflictException;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;
import pt.ua.deti.tqs.p11.domain.rules.VehicleTypeRules;

/**
 * RulesOfUseRepository backed by PostgreSQL. Plain SQL (JdbcTemplate): a version is two small
 * tables that are only ever inserted and read, so there is no entity to track. A version number
 * that is already stored is a primary key violation, reported as a conflict.
 */
@Component
class RulesOfUsePersistenceAdapter implements RulesOfUseRepository {

  private static final String SELECT =
      "SELECT version, created_at, created_by, max_booking_minutes, min_battery_percent,"
          + " start_window_minutes, repositioning_window_minutes, overtime_grace_minutes,"
          + " overtime_block_minutes, overtime_rate_per_block FROM rules_of_use";

  private final JdbcTemplate jdbc;

  RulesOfUsePersistenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<RulesOfUse> findCurrent() {
    return withVehicleTypes(jdbc.query(SELECT + " ORDER BY version DESC LIMIT 1", this::header))
        .stream()
        .findFirst();
  }

  @Override
  public List<RulesOfUse> findAll() {
    return withVehicleTypes(jdbc.query(SELECT + " ORDER BY version DESC", this::header));
  }

  @Override
  public RulesOfUse save(RulesOfUse rules) {
    try {
      jdbc.update(
          "INSERT INTO rules_of_use (version, created_at, created_by, max_booking_minutes,"
              + " min_battery_percent, start_window_minutes, repositioning_window_minutes,"
              + " overtime_grace_minutes, overtime_block_minutes, overtime_rate_per_block)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          rules.version(),
          Timestamp.from(rules.createdAt()),
          rules.createdBy(),
          rules.maxBookingDuration().toMinutes(),
          rules.minBattery().percent(),
          rules.startWindow().toMinutes(),
          rules.repositioningWindow().toMinutes(),
          rules.overtimeGrace().toMinutes(),
          rules.overtimeBlock().toMinutes(),
          rules.overtimeRatePerBlock().amount());
    } catch (DuplicateKeyException e) {
      throw new RulesVersionConflictException(rules.version());
    }
    rules
        .vehicleTypes()
        .forEach((type, perType) ->
            jdbc.update(
                "INSERT INTO rules_of_use_vehicle_types (version, vehicle_type, deposit,"
                    + " battery_percent_per_30_minutes) VALUES (?, ?, ?, ?)",
                rules.version(),
                type.name(),
                perType.deposit().amount(),
                perType.batteryPercentPer30Minutes()));
    return rules;
  }

  /**
   * A stored version without its vehicle types. The types are read only after the versions' query
   * has returned its connection: reading them inside the row mapper held two connections per read,
   * and simultaneous bookings then used up the pool waiting on each other.
   */
  private record Header(
      int version,
      Instant createdAt,
      UUID createdBy,
      Duration maxBookingDuration,
      BatteryLevel minBattery,
      Duration startWindow,
      Duration repositioningWindow,
      Duration overtimeGrace,
      Duration overtimeBlock,
      Money overtimeRatePerBlock) {}

  private Header header(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
    return new Header(
        rs.getInt("version"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getObject("created_by", UUID.class),
        Duration.ofMinutes(rs.getInt("max_booking_minutes")),
        BatteryLevel.of(rs.getInt("min_battery_percent")),
        Duration.ofMinutes(rs.getInt("start_window_minutes")),
        Duration.ofMinutes(rs.getInt("repositioning_window_minutes")),
        Duration.ofMinutes(rs.getInt("overtime_grace_minutes")),
        Duration.ofMinutes(rs.getInt("overtime_block_minutes")),
        new Money(rs.getBigDecimal("overtime_rate_per_block")));
  }

  private List<RulesOfUse> withVehicleTypes(List<Header> headers) {
    return headers.stream()
        .map(header ->
            new RulesOfUse(
                header.version(),
                header.createdAt(),
                header.createdBy(),
                header.maxBookingDuration(),
                header.minBattery(),
                header.startWindow(),
                header.repositioningWindow(),
                header.overtimeGrace(),
                header.overtimeBlock(),
                header.overtimeRatePerBlock(),
                vehicleTypes(header.version())))
        .toList();
  }

  private Map<VehicleType, VehicleTypeRules> vehicleTypes(int version) {
    Map<VehicleType, VehicleTypeRules> rules = new EnumMap<>(VehicleType.class);
    jdbc.query(
        "SELECT vehicle_type, deposit, battery_percent_per_30_minutes"
            + " FROM rules_of_use_vehicle_types WHERE version = ?",
        rs -> {
          rules.put(
              VehicleType.valueOf(rs.getString("vehicle_type")),
              new VehicleTypeRules(
                  new Money(rs.getBigDecimal("deposit")),
                  rs.getInt("battery_percent_per_30_minutes")));
        },
        version);
    return rules;
  }
}
