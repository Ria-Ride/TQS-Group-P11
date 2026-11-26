package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What was booked: who, which vehicle, with which Pass, from which hub and for which period. */
class BookingTermsTest {

  private static final UUID ANA = UUID.randomUUID();
  private static final UUID BIKE = UUID.randomUUID();
  private static final UUID PASS = UUID.randomUUID();
  private static final UUID HUB = UUID.randomUUID();
  private static final Instant START = Instant.parse("2026-11-26T10:00:00Z");

  @Test
  void plannedDuration_isFromThePlannedStartToThePlannedEnd() {
    BookingTerms terms = new BookingTerms(ANA, BIKE, PASS, HUB, START, START.plusSeconds(3600));

    assertThat(terms.plannedDuration()).isEqualTo(Duration.ofMinutes(60));
  }

  @Test
  void new_endOneMinuteAfterTheStart_isAccepted() {
    BookingTerms terms = new BookingTerms(ANA, BIKE, PASS, HUB, START, START.plusSeconds(60));

    assertThat(terms.plannedDuration()).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void new_endAtTheStart_isRefused() {
    assertThatThrownBy(() -> new BookingTerms(ANA, BIKE, PASS, HUB, START, START))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_endBeforeTheStart_isRefused() {
    assertThatThrownBy(
            () -> new BookingTerms(ANA, BIKE, PASS, HUB, START, START.minusSeconds(60)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_withoutAVehicle_isRefused() {
    assertThatThrownBy(
            () -> new BookingTerms(ANA, null, PASS, HUB, START, START.plusSeconds(60)))
        .isInstanceOf(NullPointerException.class);
  }
}