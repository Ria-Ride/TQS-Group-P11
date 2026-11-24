package pt.ua.deti.tqs.p11.domain.pass;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * A pass an account bought: what it cost and when it is valid. It exists only after an approved
 * payment (BR10), so there is no "unpaid" pass.
 *
 * @param stay the stay whose code gave the discount, or null if none was used
 */
public record Pass(
    UUID id,
    UUID accountId,
    UUID passTypeId,
    String passTypeCode,
    UUID stay,
    Money price,
    Instant validFrom,
    Instant validTo) {

  public Pass {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(passTypeId, "passTypeId");
    Objects.requireNonNull(passTypeCode, "passTypeCode");
    Objects.requireNonNull(price, "price");
    Objects.requireNonNull(validFrom, "validFrom");
    Objects.requireNonNull(validTo, "validTo");
    if (!validTo.isAfter(validFrom)) {
      throw new IllegalArgumentException(
          "A pass must end after it starts: %s to %s".formatted(validFrom, validTo));
    }
  }

  /**
   * BR8: a pass of the type, valid from the moment the payment was approved.
   *
   * @param stayId the stay whose code was used, or null
   * @param price what the account paid (the type's price, with any stay discount)
   */
  public static Pass issue(
      UUID id, UUID accountId, PassType type, UUID stayId, Money price, Instant paidAt) {
    return new Pass(
        id, accountId, type.id(), type.code(), stayId, price, paidAt, type.validUntil(paidAt));
  }

  /** The stay whose code gave the discount, if any. */
  public Optional<UUID> stayId() {
    return Optional.ofNullable(stay);
  }
}
