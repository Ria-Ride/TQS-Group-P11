package pt.ua.deti.tqs.p11.domain.fleet;

/**
 * Whether a vehicle can be booked now, and if not, why (BR1, BR2). The reason is what a tourist
 * sees next to a vehicle that cannot be selected.
 */
public enum Bookability {
  BOOKABLE,
  /** BR2: only a vehicle in state Available can be booked. */
  NOT_AVAILABLE,
  /** BR1: no battery reading has arrived yet, so the level is unknown. */
  NO_BATTERY_READING,
  /** BR1: the battery is below the minimum. */
  LOW_BATTERY,
  /** BR1: the battery is above the minimum but not enough for the duration of the ride. */
  INSUFFICIENT_FOR_DURATION;

  public boolean isBookable() {
    return this == BOOKABLE;
  }
}
