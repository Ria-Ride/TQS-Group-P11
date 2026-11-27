package pt.ua.deti.tqs.p11.application.pass;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.domain.pass.PartnerAccommodation;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/**
 * What the client manager does with partner hotels and their stay codes (US14, BR9): register a
 * hotel with its discount and issue a code for a stay. The platform makes the code and asks for
 * another if it is taken, so two codes never clash. Redeeming a code is the tourist's side (US03).
 */
public class StayManagementUseCase {

  /**
   * Enough to find a free code even when many are taken; a failure means the generator is broken.
   */
  private static final int MAX_ATTEMPTS = 10;

  private final PartnerAccommodationRepository accommodations;
  private final StayRepository stays;
  private final StayCodeGenerator codes;
  private final Supplier<UUID> ids;

  public StayManagementUseCase(
      PartnerAccommodationRepository accommodations,
      StayRepository stays,
      StayCodeGenerator codes,
      Supplier<UUID> ids) {
    this.accommodations = Objects.requireNonNull(accommodations, "accommodations");
    this.stays = Objects.requireNonNull(stays, "stays");
    this.codes = Objects.requireNonNull(codes, "codes");
    this.ids = Objects.requireNonNull(ids, "ids");
  }

  /**
   * @throws AccommodationNameAlreadyInUseException if another hotel has the name
   * @throws IllegalArgumentException if the discount is not from 0 to 100
   */
  public PartnerAccommodation registerAccommodation(String name, int discountPercent) {
    PartnerAccommodation hotel = new PartnerAccommodation(ids.get(), name, discountPercent);
    if (accommodations.existsByName(name)) {
      throw new AccommodationNameAlreadyInUseException(name);
    }
    return accommodations.save(hotel);
  }

  /**
   * Issues a code for a stay, valid from check-in to check-out, both days included (BR9).
   *
   * @throws UnknownAccommodationException if there is no such hotel
   * @throws IllegalArgumentException if check-out is before check-in
   */
  public Stay issue(UUID accommodationId, LocalDate checkIn, LocalDate checkOut) {
    PartnerAccommodation hotel =
        accommodations
            .findById(accommodationId)
            .orElseThrow(() -> new UnknownAccommodationException(accommodationId));
    if (checkOut.isBefore(checkIn)) {
      throw new IllegalArgumentException(
          "Check-out %s is before check-in %s".formatted(checkOut, checkIn));
    }
    Stay stay = new Stay(ids.get(), freeCode(), hotel, checkIn, checkOut, null);
    stays.add(stay);
    return stay;
  }

  private String freeCode() {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      String code = codes.next().strip().toUpperCase(Locale.ROOT);
      if (!stays.existsByCode(code)) {
        return code;
      }
    }
    throw new IllegalStateException(
        "No free stay code found after %d tries".formatted(MAX_ATTEMPTS));
  }

  public List<PartnerAccommodation> accommodations() {
    return accommodations.findAll();
  }

  /** Every stay, newest check-in first. */
  public List<Stay> stays() {
    return stays.findAll();
  }
}
