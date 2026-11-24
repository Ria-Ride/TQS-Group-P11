package pt.ua.deti.tqs.p11.application.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.DirectTransactionRunner;
import pt.ua.deti.tqs.p11.application.billing.ChargeRequest;
import pt.ua.deti.tqs.p11.application.billing.ChargeResult;
import pt.ua.deti.tqs.p11.application.billing.FakePaymentAdapter;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.billing.PaymentState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.pass.PartnerAccommodation;
import pt.ua.deti.tqs.p11.domain.pass.Pass;
import pt.ua.deti.tqs.p11.domain.pass.PassType;
import pt.ua.deti.tqs.p11.domain.pass.Stay;
import pt.ua.deti.tqs.p11.domain.pass.StayCodeLinkedToAnotherAccountException;
import pt.ua.deti.tqs.p11.domain.pass.StayCodeNotValidTodayException;

/**
 * Buying a pass (US03): BR8 validity, BR9 stay code, BR10 no pass without an approved payment. The
 * repositories are mocked and the gateway is the in-memory fake; the domain rules themselves are
 * tested in PassTypeTest and StayTest.
 */
@ExtendWith(MockitoExtension.class)
class PassPurchaseUseCaseTest {

  /** 23:30 in UTC is already 00:30 of 1 July in Lisbon (summer time, UTC+1). */
  private static final Instant NOW = Instant.parse("2026-06-30T23:30:00Z");

  private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");
  private static final LocalDate LISBON_TODAY = LocalDate.parse("2026-07-01");
  private static final UUID ANA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
  private static final UUID RUI = UUID.fromString("a0000000-0000-4000-8000-000000000002");
  private static final UUID PAYMENT_ID = UUID.fromString("b0000000-0000-4000-8000-000000000001");
  private static final UUID PASS_ID = UUID.fromString("b0000000-0000-4000-8000-000000000002");
  private static final PassType DAY =
      new PassType(
          UUID.fromString("7a1e0000-0000-4000-8000-000000000001"),
          "DAY",
          "Day pass",
          1,
          Money.of("10.00"),
          Set.of(VehicleType.E_BIKE, VehicleType.E_SCOOTER));
  private static final PartnerAccommodation RIA_AZUL =
      new PartnerAccommodation(UUID.randomUUID(), "Hotel Ria Azul", 10);

  @Mock private PassTypeRepository passTypes;
  @Mock private StayRepository stays;
  @Mock private PassRepository passes;
  @Mock private PaymentRepository payments;

  private FakePaymentAdapter gateway;
  private PassPurchaseUseCase useCase;

  @BeforeEach
  void setUp() {
    gateway = new FakePaymentAdapter();
    Iterator<UUID> ids = List.of(PAYMENT_ID, PASS_ID).iterator();
    useCase =
        new PassPurchaseUseCase(
            passTypes,
            stays,
            passes,
            payments,
            gateway,
            new DirectTransactionRunner(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            LISBON,
            ids::next);
  }

  private void dayPassExists() {
    when(passTypes.findById(DAY.id())).thenReturn(Optional.of(DAY));
  }

  private static Stay stay(LocalDate checkIn, LocalDate checkOut, UUID redeemedBy) {
    return new Stay(UUID.randomUUID(), "RIA-1001", RIA_AZUL, checkIn, checkOut, redeemedBy);
  }

  /** A stay that includes the hotel's today, not linked yet. */
  private Stay validStay() {
    Stay stay = stay(LISBON_TODAY.minusDays(1), LISBON_TODAY.plusDays(1), null);
    when(stays.findByCodeForUpdate("RIA-1001")).thenReturn(Optional.of(stay));
    return stay;
  }

  private static BuyPassCommand buy(UUID account, String stayCode) {
    return new BuyPassCommand(account, DAY.id(), stayCode, "tok_approve");
  }

  private Payment savedPayment() {
    ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
    verify(payments).save(saved.capture());
    return saved.getValue();
  }

  // ------------------------------------------------------------- approved (BR8)

  @Test
  void buy_approved_issuesAPassValidFromNowAtTheBasePrice() {
    dayPassExists();

    Pass pass = useCase.buy(buy(ANA, null));

    assertThat(pass.id()).isEqualTo(PASS_ID);
    assertThat(pass.accountId()).isEqualTo(ANA);
    assertThat(pass.price()).isEqualTo(Money.of("10.00"));
    assertThat(pass.validFrom()).isEqualTo(NOW);
    assertThat(pass.validTo()).isEqualTo(NOW.plusSeconds(24 * 3600));
    assertThat(pass.stayId()).isEmpty();
    verify(passes).save(pass);
  }

  @Test
  void buy_chargesThePriceWithThePaymentIdAndTheTouristsToken() {
    dayPassExists();

    useCase.buy(new BuyPassCommand(ANA, DAY.id(), null, "tok_approve"));

    assertThat(gateway.charges())
        .containsExactly(new ChargeRequest(PAYMENT_ID, Money.of("10.00"), "tok_approve"));
  }

  @Test
  void buy_approved_recordsACapturedPaymentForThePass() {
    dayPassExists();
    gateway.answering(new ChargeResult.Approved("ch_42"));

    useCase.buy(buy(ANA, null));

    assertThat(savedPayment())
        .isEqualTo(Payment.captured(PAYMENT_ID, ANA, Money.of("10.00"), "ch_42", PASS_ID, NOW));
  }

  // ------------------------------------------------------------- stay code (BR9)

  @Test
  void buy_withAValidStayCode_paysTheDiscountedPriceAndLinksTheCode() {
    dayPassExists();
    Stay stay = validStay();

    Pass pass = useCase.buy(buy(ANA, "RIA-1001"));

    assertThat(pass.price()).isEqualTo(Money.of("9.00"));
    assertThat(pass.stayId()).contains(stay.id());
    assertThat(gateway.charges())
        .singleElement()
        .extracting(ChargeRequest::amount)
        .isEqualTo(Money.of("9.00"));
    assertThat(stay.redeemedBy()).contains(ANA);
    verify(stays).save(stay);
  }

  @Test
  void buy_stayCodeInLowerCaseWithSpaces_isFound() {
    dayPassExists();
    validStay();

    assertThat(useCase.buy(buy(ANA, "  ria-1001 ")).price()).isEqualTo(Money.of("9.00"));
  }

  @Test
  void buy_blankStayCode_paysTheBasePrice() {
    dayPassExists();

    assertThat(useCase.buy(buy(ANA, " ")).price()).isEqualTo(Money.of("10.00"));
    verify(stays, never()).findByCodeForUpdate(any());
  }

  @Test
  void buy_unknownStayCode_isRefusedBeforeAnyCharge() {
    dayPassExists();
    when(stays.findByCodeForUpdate("NOPE-0000")).thenReturn(Optional.empty());
    BuyPassCommand command = buy(ANA, "NOPE-0000");

    assertThatThrownBy(() -> useCase.buy(command)).isInstanceOf(UnknownStayCodeException.class);
    assertThat(gateway.charges()).isEmpty();
    verify(passes, never()).save(any());
  }

  /** The check-out day is 30 June, but in Lisbon it is already 1 July: the code is over. */
  @Test
  void buy_stayCodeEndedYesterdayInTheHotelsZone_isRefusedEvenIfItIsStillTheDayInUtc() {
    dayPassExists();
    when(stays.findByCodeForUpdate("RIA-1001"))
        .thenReturn(
            Optional.of(stay(LocalDate.parse("2026-06-28"), LocalDate.parse("2026-06-30"), null)));
    BuyPassCommand command = buy(ANA, "RIA-1001");

    assertThatThrownBy(() -> useCase.buy(command))
        .isInstanceOf(StayCodeNotValidTodayException.class);
    assertThat(gateway.charges()).isEmpty();
  }

  @Test
  void buy_stayCodeOnItsCheckOutDayInTheHotelsZone_isAccepted() {
    dayPassExists();
    when(stays.findByCodeForUpdate("RIA-1001"))
        .thenReturn(Optional.of(stay(LocalDate.parse("2026-06-29"), LISBON_TODAY, null)));

    assertThat(useCase.buy(buy(ANA, "RIA-1001")).price()).isEqualTo(Money.of("9.00"));
  }

  @Test
  void buy_stayCodeOfAnotherAccount_isRefusedBeforeAnyCharge() {
    dayPassExists();
    when(stays.findByCodeForUpdate("RIA-1001"))
        .thenReturn(Optional.of(stay(LISBON_TODAY.minusDays(1), LISBON_TODAY.plusDays(1), RUI)));
    BuyPassCommand command = buy(ANA, "RIA-1001");

    assertThatThrownBy(() -> useCase.buy(command))
        .isInstanceOf(StayCodeLinkedToAnotherAccountException.class);
    assertThat(gateway.charges()).isEmpty();
  }

  // ------------------------------------------------------------- not approved (BR10)

  @Test
  void buy_declined_issuesNoPassAndRecordsADeclinedPayment() {
    dayPassExists();
    gateway.answering(new ChargeResult.Declined("card_declined"));
    BuyPassCommand command = buy(ANA, null);

    assertThatThrownBy(() -> useCase.buy(command))
        .isInstanceOfSatisfying(
            PaymentDeclinedException.class, e -> assertThat(e.reason()).isEqualTo("card_declined"));
    verify(passes, never()).save(any());
    assertThat(savedPayment())
        .isEqualTo(Payment.declined(PAYMENT_ID, ANA, Money.of("10.00"), "card_declined", NOW));
  }

  @Test
  void buy_timedOut_issuesNoPassAndRecordsADeclinedPayment() {
    dayPassExists();
    gateway.answering(new ChargeResult.TimedOut());
    BuyPassCommand command = buy(ANA, null);

    assertThatThrownBy(() -> useCase.buy(command)).isInstanceOf(PaymentTimedOutException.class);
    verify(passes, never()).save(any());
    assertThat(savedPayment().state()).isEqualTo(PaymentState.DECLINED);
    assertThat(savedPayment().failureReason()).contains("timeout");
  }

  /** The code was linked by this purchase only: a failed payment frees it for anyone again. */
  @Test
  void buy_declinedAfterLinkingTheCode_unlinksIt() {
    dayPassExists();
    Stay stay = validStay();
    gateway.answering(new ChargeResult.Declined("card_declined"));
    BuyPassCommand command = buy(ANA, "RIA-1001");

    assertThatThrownBy(() -> useCase.buy(command)).isInstanceOf(PaymentDeclinedException.class);
    assertThat(stay.redeemedBy()).isEmpty();
  }

  /** The code was already Ana's from an earlier pass: a failed payment now does not free it. */
  @Test
  void buy_declinedWithACodeAlreadyLinkedToTheAccount_keepsTheLink() {
    dayPassExists();
    Stay stay = stay(LISBON_TODAY.minusDays(1), LISBON_TODAY.plusDays(1), ANA);
    when(stays.findByCodeForUpdate("RIA-1001")).thenReturn(Optional.of(stay));
    gateway.answering(new ChargeResult.TimedOut());
    BuyPassCommand command = buy(ANA, "RIA-1001");

    assertThatThrownBy(() -> useCase.buy(command)).isInstanceOf(PaymentTimedOutException.class);
    assertThat(stay.redeemedBy()).contains(ANA);
  }

  // ------------------------------------------------------------- other

  @Test
  void buy_unknownPassType_isRefusedBeforeAnyCharge() {
    when(passTypes.findById(DAY.id())).thenReturn(Optional.empty());
    BuyPassCommand command = buy(ANA, null);

    assertThatThrownBy(() -> useCase.buy(command)).isInstanceOf(UnknownPassTypeException.class);
    assertThat(gateway.charges()).isEmpty();
  }

  @Test
  void passesOf_returnsTheAccountsPasses() {
    Pass pass = Pass.issue(PASS_ID, ANA, DAY, null, Money.of("10.00"), NOW);
    when(passes.findByAccount(ANA)).thenReturn(List.of(pass));

    assertThat(useCase.passesOf(ANA)).containsExactly(pass);
  }
}
