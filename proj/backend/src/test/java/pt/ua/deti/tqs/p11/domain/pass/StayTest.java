package pt.ua.deti.tqs.p11.domain.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * BR9: a stay code is accepted from check-in to check-out, both days included, and links to the
 * first account that buys a pass with it.
 */
class StayTest {

  private static final LocalDate CHECK_IN = LocalDate.parse("2026-11-23");
  private static final LocalDate CHECK_OUT = LocalDate.parse("2026-11-25");
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID RUI = UUID.randomUUID();

  private static Stay stay(UUID redeemedBy) {
    return new Stay(
        UUID.randomUUID(),
        "RIA-1001",
        new PartnerAccommodation(UUID.randomUUID(), "Hotel Ria Azul", 10),
        CHECK_IN,
        CHECK_OUT,
        redeemedBy);
  }

  @Test
  void new_checkOutBeforeCheckIn_isRefused() {
    PartnerAccommodation hotel = new PartnerAccommodation(UUID.randomUUID(), "Hotel Ria Azul", 10);
    UUID id = UUID.randomUUID();
    LocalDate dayBefore = CHECK_IN.minusDays(1);

    assertThatThrownBy(() -> new Stay(id, "RIA-1001", hotel, CHECK_IN, dayBefore, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_checkOutOnCheckIn_isAccepted() {
    PartnerAccommodation hotel = new PartnerAccommodation(UUID.randomUUID(), "Hotel Ria Azul", 10);

    Stay oneNight = new Stay(UUID.randomUUID(), "RIA-1001", hotel, CHECK_IN, CHECK_IN, null);

    assertThat(oneNight.checkOut()).isEqualTo(CHECK_IN);
  }

  // ------------------------------------------------------------- dates (inclusive)

  @ParameterizedTest(name = "{0} days after check-in is accepted")
  @ValueSource(ints = {0, 1, 2})
  void redeemBy_fromCheckInToCheckOut_isAccepted(int daysAfterCheckIn) {
    Stay stay = stay(null);

    stay.redeemBy(ANA, CHECK_IN.plusDays(daysAfterCheckIn));

    assertThat(stay.redeemedBy()).contains(ANA);
  }

  @Test
  void redeemBy_theDayBeforeCheckIn_isRefused() {
    Stay stay = stay(null);
    LocalDate dayBefore = CHECK_IN.minusDays(1);

    assertThatThrownBy(() -> stay.redeemBy(ANA, dayBefore))
        .isInstanceOf(StayCodeNotValidTodayException.class);
    assertThat(stay.redeemedBy()).isEmpty();
  }

  @Test
  void redeemBy_theDayAfterCheckOut_isRefused() {
    Stay stay = stay(null);
    LocalDate dayAfter = CHECK_OUT.plusDays(1);

    assertThatThrownBy(() -> stay.redeemBy(ANA, dayAfter))
        .isInstanceOf(StayCodeNotValidTodayException.class);
    assertThat(stay.redeemedBy()).isEmpty();
  }

  // ------------------------------------------------------------- ownership

  @Test
  void redeemBy_anUnlinkedCode_linksItAndSaysSo() {
    Stay stay = stay(null);

    assertThat(stay.redeemBy(ANA, CHECK_IN)).isTrue();
    assertThat(stay.redeemedBy()).contains(ANA);
  }

  @Test
  void redeemBy_theAccountItIsLinkedTo_isAcceptedAndLinksNothingNew() {
    Stay stay = stay(ANA);

    assertThat(stay.redeemBy(ANA, CHECK_IN)).isFalse();
    assertThat(stay.redeemedBy()).contains(ANA);
  }

  @Test
  void redeemBy_anotherAccount_isRefusedAndTheLinkStays() {
    Stay stay = stay(RUI);

    assertThatThrownBy(() -> stay.redeemBy(ANA, CHECK_IN))
        .isInstanceOf(StayCodeLinkedToAnotherAccountException.class);
    assertThat(stay.redeemedBy()).contains(RUI);
  }

  /** The date is checked first: a stale code is "not valid today" whoever owns it. */
  @Test
  void redeemBy_anotherAccountAfterCheckOut_isRefusedForTheDate() {
    Stay stay = stay(RUI);
    LocalDate dayAfter = CHECK_OUT.plusDays(1);

    assertThatThrownBy(() -> stay.redeemBy(ANA, dayAfter))
        .isInstanceOf(StayCodeNotValidTodayException.class);
  }

  // ------------------------------------------------------------- checking without linking (quote)

  @Test
  void checkRedeemableBy_aValidCode_linksNothing() {
    Stay stay = stay(null);

    stay.checkRedeemableBy(ANA, CHECK_IN);

    assertThat(stay.redeemedBy()).isEmpty();
  }

  @Test
  void checkRedeemableBy_theDayAfterCheckOut_isRefused() {
    Stay stay = stay(null);
    LocalDate dayAfter = CHECK_OUT.plusDays(1);

    assertThatThrownBy(() -> stay.checkRedeemableBy(ANA, dayAfter))
        .isInstanceOf(StayCodeNotValidTodayException.class);
  }

  @Test
  void checkRedeemableBy_anotherAccount_isRefused() {
    Stay stay = stay(RUI);

    assertThatThrownBy(() -> stay.checkRedeemableBy(ANA, CHECK_IN))
        .isInstanceOf(StayCodeLinkedToAnotherAccountException.class);
  }

  // ------------------------------------------------------------- undo after a failed payment

  @Test
  void unlink_afterAFailedPayment_freesTheCodeForAnyAccount() {
    Stay stay = stay(null);
    stay.redeemBy(ANA, CHECK_IN);

    stay.unlink();

    assertThat(stay.redeemedBy()).isEmpty();
    assertThat(stay.redeemBy(RUI, CHECK_IN)).isTrue();
  }

  @Test
  void discountPercent_isTheDiscountOfTheHotel() {
    assertThat(stay(null).discountPercent()).isEqualTo(10);
  }
}
