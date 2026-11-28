package pt.ua.deti.tqs.p11.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The platform clock in the acceptance tests. It follows the real time, like Clock.systemUTC(),
 * until a scenario fixes it; then it stays at the fixed instant until a step moves it, so time
 * boundaries (a start window of exactly 15:00, an expiry at 15:01) are checked without waiting and
 * without depending on how fast the steps run. Every scenario gets the real time back afterwards.
 *
 * <p>Thread-safe: the application reads it from request and scheduler threads while a step moves
 * it.
 */
public final class ShiftableClock extends Clock {

  /** Null while the clock follows the real time. */
  private final AtomicReference<Instant> fixed = new AtomicReference<>();

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  /** Only UTC: every instant of the platform is kept in UTC. */
  @Override
  public Clock withZone(ZoneId zone) {
    if (!ZoneOffset.UTC.equals(zone)) {
      throw new UnsupportedOperationException("The platform clock is always in UTC");
    }
    return this;
  }

  @Override
  public Instant instant() {
    Instant at = fixed.get();
    return at != null ? at : Instant.now();
  }

  /** From now on, the clock shows this instant until it is moved or released. */
  public void fixAt(Instant at) {
    fixed.set(Objects.requireNonNull(at, "at"));
  }

  /** Back to the real time. */
  public void release() {
    fixed.set(null);
  }
}
