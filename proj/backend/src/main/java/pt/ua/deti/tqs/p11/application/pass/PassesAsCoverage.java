package pt.ua.deti.tqs.p11.application.pass;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import pt.ua.deti.tqs.p11.application.booking.PassCoverages;
import pt.ua.deti.tqs.p11.domain.booking.PassCoverage;
import pt.ua.deti.tqs.p11.domain.pass.Pass;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/**
 * PassCoverages from the stored passes and pass types (US03): each pass keeps its validity and gets
 * the vehicle types of its type. The pass types are few, so they are read all at once.
 */
public class PassesAsCoverage implements PassCoverages {

  private final PassRepository passes;
  private final PassTypeRepository types;

  public PassesAsCoverage(PassRepository passes, PassTypeRepository types) {
    this.passes = Objects.requireNonNull(passes, "passes");
    this.types = Objects.requireNonNull(types, "types");
  }

  /**
   * @throws IllegalStateException if a pass points to a type that is not stored
   */
  @Override
  public List<PassCoverage> ofAccount(UUID accountId) {
    List<Pass> owned = passes.findByAccount(accountId);
    if (owned.isEmpty()) {
      return List.of();
    }
    Map<UUID, PassType> byId =
        types.findAll().stream().collect(Collectors.toMap(PassType::id, Function.identity()));
    return owned.stream().map(pass -> coverage(pass, byId)).toList();
  }

  private static PassCoverage coverage(Pass pass, Map<UUID, PassType> types) {
    PassType type = types.get(pass.passTypeId());
    if (type == null) {
      throw new IllegalStateException(
          "Pass %s has a type that is not stored: %s".formatted(pass.id(), pass.passTypeId()));
    }
    return new PassCoverage(pass.id(), pass.validFrom(), pass.validTo(), type.vehicleTypes());
  }
}
