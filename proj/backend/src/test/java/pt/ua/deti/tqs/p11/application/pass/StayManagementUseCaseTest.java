package pt.ua.deti.tqs.p11.application.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.domain.pass.PartnerAccommodation;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/**
 * What the client manager does with partner hotels and their stay codes (US14, BR9): register a
 * hotel with its discount, issue a code for a stay, read both lists. The platform makes the code,
 * so two codes never clash; the rules of redeeming a code are tested in StayTest and
 * PassPurchaseUseCaseTest.
 */
@ExtendWith(MockitoExtension.class)
class StayManagementUseCaseTest {

  private static final UUID HOTEL_ID = UUID.fromString("5a7e0000-0000-4000-8000-000000000001");
  private static final UUID NEW_ID = UUID.fromString("57a90000-0000-4000-8000-0000000000aa");
  private static final PartnerAccommodation HOTEL =
      new PartnerAccommodation(HOTEL_ID, "Hotel Ria Azul", 10);
  private static final LocalDate IN = LocalDate.of(2026, 12, 1);
  private static final LocalDate OUT = LocalDate.of(2026, 12, 5);

  @Mock private PartnerAccommodationRepository accommodations;
  @Mock private StayRepository stays;

  private final Deque<String> codes = new ArrayDeque<>();
  private StayManagementUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new StayManagementUseCase(accommodations, stays, codes::pop, () -> NEW_ID);
  }

  // ------------------------------------------------------------- hotels

  @Test
  void registerAccommodation_storesTheHotelWithItsDiscount() {
    when(accommodations.existsByName("Hotel Laguna")).thenReturn(false);
    when(accommodations.save(any(PartnerAccommodation.class)))
        .thenAnswer(call -> call.getArgument(0));

    PartnerAccommodation created = useCase.registerAccommodation("Hotel Laguna", 15);

    ArgumentCaptor<PartnerAccommodation> saved =
        ArgumentCaptor.forClass(PartnerAccommodation.class);
    verify(accommodations).save(saved.capture());
    assertThat(saved.getValue()).isEqualTo(created);
    assertThat(created.id()).isEqualTo(NEW_ID);
    assertThat(created.name()).isEqualTo("Hotel Laguna");
    assertThat(created.discountPercent()).isEqualTo(15);
  }

  @Test
  void registerAccommodation_nameAlreadyInUse_isRefused() {
    when(accommodations.existsByName("Hotel Ria Azul")).thenReturn(true);

    assertThatThrownBy(() -> useCase.registerAccommodation("Hotel Ria Azul", 10))
        .isInstanceOf(AccommodationNameAlreadyInUseException.class);
    verify(accommodations, never()).save(any());
  }

  @Test
  void registerAccommodation_discountOutsideZeroToHundred_isRefused() {
    assertThatThrownBy(() -> useCase.registerAccommodation("Hotel X", 101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.registerAccommodation("Hotel X", -1))
        .isInstanceOf(IllegalArgumentException.class);
    verify(accommodations, never()).save(any());
  }

  // ------------------------------------------------------------- stay codes

  @Test
  void issue_storesAStayWithTheGeneratedCode_notYetRedeemed() {
    codes.add("RIA-7K3M9Q");
    when(accommodations.findById(HOTEL_ID)).thenReturn(Optional.of(HOTEL));
    when(stays.existsByCode("RIA-7K3M9Q")).thenReturn(false);

    Stay issued = useCase.issue(HOTEL_ID, IN, OUT);

    ArgumentCaptor<Stay> saved = ArgumentCaptor.forClass(Stay.class);
    verify(stays).add(saved.capture());
    assertThat(saved.getValue()).isSameAs(issued);
    assertThat(issued.id()).isEqualTo(NEW_ID);
    assertThat(issued.code()).isEqualTo("RIA-7K3M9Q");
    assertThat(issued.accommodation()).isEqualTo(HOTEL);
    assertThat(issued.checkIn()).isEqualTo(IN);
    assertThat(issued.checkOut()).isEqualTo(OUT);
    assertThat(issued.redeemedBy()).isEmpty();
  }

  @Test
  void issue_codeAlreadyTaken_triesAnotherOne() {
    codes.add("RIA-TAKEN1");
    codes.add("RIA-FREE22");
    when(accommodations.findById(HOTEL_ID)).thenReturn(Optional.of(HOTEL));
    when(stays.existsByCode("RIA-TAKEN1")).thenReturn(true);
    when(stays.existsByCode("RIA-FREE22")).thenReturn(false);

    Stay issued = useCase.issue(HOTEL_ID, IN, OUT);

    assertThat(issued.code()).isEqualTo("RIA-FREE22");
  }

  @Test
  void issue_everyCodeTaken_givesUpInsteadOfLoopingForever() {
    for (int i = 0; i < 20; i++) {
      codes.add("RIA-TAKEN" + i);
    }
    when(accommodations.findById(HOTEL_ID)).thenReturn(Optional.of(HOTEL));
    when(stays.existsByCode(any())).thenReturn(true);

    assertThatThrownBy(() -> useCase.issue(HOTEL_ID, IN, OUT))
        .isInstanceOf(IllegalStateException.class);
    verify(stays, never()).add(any());
  }

  @Test
  void issue_unknownHotel_isRefused() {
    when(accommodations.findById(HOTEL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.issue(HOTEL_ID, IN, OUT))
        .isInstanceOf(UnknownAccommodationException.class);
    verify(stays, never()).add(any());
  }

  @Test
  void issue_checkOutBeforeCheckIn_isRefused_andOneDayStayIsAccepted() {
    codes.add("RIA-ONEDAY");
    when(accommodations.findById(HOTEL_ID)).thenReturn(Optional.of(HOTEL));
    when(stays.existsByCode(any())).thenReturn(false);

    assertThat(useCase.issue(HOTEL_ID, IN, IN).checkOut()).isEqualTo(IN);
    assertThatThrownBy(() -> useCase.issue(HOTEL_ID, OUT, IN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lists_areWhatTheRepositoriesHold() {
    when(accommodations.findAll()).thenReturn(List.of(HOTEL));
    Stay stay = new Stay(NEW_ID, "RIA-X", HOTEL, IN, OUT, null);
    when(stays.findAll()).thenReturn(List.of(stay));

    assertThat(useCase.accommodations()).containsExactly(HOTEL);
    assertThat(useCase.stays()).containsExactly(stay);
  }
}
