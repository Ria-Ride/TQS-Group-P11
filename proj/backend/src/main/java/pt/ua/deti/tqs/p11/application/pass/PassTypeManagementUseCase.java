package pt.ua.deti.tqs.p11.application.pass;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/**
 * What the client manager does with pass types (US14, BR8): create one, or change its name,
 * validity, base price and allowed vehicle types. The code never changes. A pass already bought
 * keeps the price and dates stored on it, so a change only reaches later purchases.
 */
public class PassTypeManagementUseCase {

  private final PassTypeRepository passTypes;
  private final TransactionRunner transactions;
  private final Supplier<UUID> ids;

  public PassTypeManagementUseCase(
      PassTypeRepository passTypes, TransactionRunner transactions, Supplier<UUID> ids) {
    this.passTypes = Objects.requireNonNull(passTypes, "passTypes");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.ids = Objects.requireNonNull(ids, "ids");
  }

  /**
   * @throws PassTypeCodeAlreadyInUseException if another pass type has the code
   * @throws IllegalArgumentException if a value is invalid
   */
  public PassType create(CreatePassTypeCommand command) {
    PassType passType =
        new PassType(
            ids.get(),
            command.code(),
            command.name(),
            command.validityDays(),
            command.basePrice(),
            command.vehicleTypes());
    return transactions.inTransaction(() -> {
      if (passTypes.existsByCode(passType.code())) {
        throw new PassTypeCodeAlreadyInUseException(passType.code());
      }
      return passTypes.save(passType);
    });
  }

  /**
   * @throws PassTypeNotFoundException if there is no such pass type
   * @throws IllegalArgumentException if a value is invalid
   */
  public PassType update(UUID id, UpdatePassTypeCommand command) {
    return transactions.inTransaction(() -> {
      PassType old = passTypes.findById(id).orElseThrow(() -> new PassTypeNotFoundException(id));
      PassType changed =
          new PassType(
              old.id(),
              old.code(),
              command.name() != null ? command.name() : old.name(),
              command.validityDays() != null ? command.validityDays() : old.validityDays(),
              command.basePrice() != null ? command.basePrice() : old.basePrice(),
              command.vehicleTypes() != null ? command.vehicleTypes() : old.vehicleTypes());
      return passTypes.save(changed);
    });
  }

  /** Every pass type, shortest validity first. */
  public List<PassType> list() {
    return passTypes.findAll();
  }
}
