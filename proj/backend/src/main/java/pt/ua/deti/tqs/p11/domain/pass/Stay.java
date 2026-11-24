package pt.ua.deti.tqs.p11.domain.pass;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A guest's stay at a partner hotel, identified by the code the hotel gives the guest. BR9: the
 * code is accepted from check-in to check-out, both days included, and links to the first account
 * that gets a pass with it; that account may use it again during the stay, any other is refused.
 *
 * <p>Dates are calendar days at the hotel. The caller turns "now" into the hotel's date, so this
 * class never reads a clock.
 */
public class Stay {

  private final UUID id;
  private final String code;
  private final PartnerAccommodation accommodation;
  private final LocalDate checkIn;
  private final LocalDate checkOut;
  private UUID redeemedBy;

  /**
   * @param redeemedBy the account the code is linked to, or null if none yet
   * @throws IllegalArgumentException if check-out is before check-in
   */
  public Stay(
      UUID id,
      String code,
      PartnerAccommodation accommodation,
      LocalDate checkIn,
      LocalDate checkOut,
      UUID redeemedBy) {
    this.id = Objects.requireNonNull(id, "id");
    this.code = Objects.requireNonNull(code, "code");
    this.accommodation = Objects.requireNonNull(accommodation, "accommodation");
    this.checkIn = Objects.requireNonNull(checkIn, "checkIn");
    this.checkOut = Objects.requireNonNull(checkOut, "checkOut");
    if (checkOut.isBefore(checkIn)) {
      throw new IllegalArgumentException(
          "Check-out %s is before check-in %s".formatted(checkOut, checkIn));
    }
    this.redeemedBy = redeemedBy;
  }

  /**
   * Uses the code for the account on the hotel's date {@code today}. The date is checked first, so
   * an old code is reported as not valid whoever it belongs to.
   *
   * @return true if this call linked the code to the account, false if it was already linked to it.
   *     The caller undoes a new link with {@link #unlink()} when the payment fails.
   * @throws StayCodeNotValidTodayException before check-in or after check-out
   * @throws StayCodeLinkedToAnotherAccountException if another account already uses the code
   */
  public boolean redeemBy(UUID account, LocalDate today) {
    Objects.requireNonNull(account, "account");
    if (today.isBefore(checkIn) || today.isAfter(checkOut)) {
      throw new StayCodeNotValidTodayException(code, today, checkIn, checkOut);
    }
    if (redeemedBy == null) {
      redeemedBy = account;
      return true;
    }
    if (!redeemedBy.equals(account)) {
      throw new StayCodeLinkedToAnotherAccountException(code);
    }
    return false;
  }

  /** Frees the code again, after a payment that followed a new link was not approved (BR10). */
  public void unlink() {
    redeemedBy = null;
  }

  public UUID id() {
    return id;
  }

  public String code() {
    return code;
  }

  public PartnerAccommodation accommodation() {
    return accommodation;
  }

  public LocalDate checkIn() {
    return checkIn;
  }

  public LocalDate checkOut() {
    return checkOut;
  }

  public Optional<UUID> redeemedBy() {
    return Optional.ofNullable(redeemedBy);
  }

  /** The discount of the hotel of this stay. */
  public int discountPercent() {
    return accommodation.discountPercent();
  }
}
