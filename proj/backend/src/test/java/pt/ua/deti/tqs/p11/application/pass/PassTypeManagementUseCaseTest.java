package pt.ua.deti.tqs.p11.application.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/**
 * What the client manager does with pass types (US14, BR8): create one, change its name, validity,
 * base price or allowed vehicle types. The code is fixed once made. A pass already bought keeps the
 * price and dates it was bought with (they are stored on the pass), so a change only reaches later
 * purchases. The validity of a pass type is tested in PassTypeTest.
 */
@ExtendWith(MockitoExtension.class)
class PassTypeManagementUseCaseTest {

  private static final UUID NEW_ID = UUID.fromString("7a1e0000-0000-4000-8000-0000000000aa");
  private static final UUID DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000001");
  private static final PassType DAY =
      new PassType(
          DAY_ID,
          "DAY",
          "Day pass",
          1,
          Money.of("10.00"),
          Set.of(VehicleType.E_BIKE, VehicleType.E_SCOOTER));

  @Mock private PassTypeRepository passTypes;

  private PassTypeManagementUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new PassTypeManagementUseCase(passTypes, new DirectTransactionRunner(), () -> NEW_ID);
  }

  private PassType saved() {
    ArgumentCaptor<PassType> saved = ArgumentCaptor.forClass(PassType.class);
    verify(passTypes).save(saved.capture());
    return saved.getValue();
  }

  // ------------------------------------------------------------- create

  @Test
  void create_storesANewPassTypeWithAGeneratedId() {
    when(passTypes.existsByCode("WEEK")).thenReturn(false);
    when(passTypes.save(any(PassType.class))).thenAnswer(call -> call.getArgument(0));

    PassType created =
        useCase.create(
            new CreatePassTypeCommand(
                "WEEK", "Week pass", 7, Money.of("40"), Set.of(VehicleType.E_BIKE)));

    assertThat(saved()).isEqualTo(created);
    assertThat(created.id()).isEqualTo(NEW_ID);
    assertThat(created.code()).isEqualTo("WEEK");
    assertThat(created.validityDays()).isEqualTo(7);
    assertThat(created.basePrice()).isEqualTo(Money.of("40.00"));
    assertThat(created.vehicleTypes()).containsExactly(VehicleType.E_BIKE);
  }

  @Test
  void create_codeAlreadyInUse_isRefusedAndStoresNothing() {
    when(passTypes.existsByCode("DAY")).thenReturn(true);

    assertThatThrownBy(() ->
            useCase.create(
                new CreatePassTypeCommand(
                    "DAY", "Another", 1, Money.of("5"), Set.of(VehicleType.E_BIKE))))
        .isInstanceOf(PassTypeCodeAlreadyInUseException.class);
    verify(passTypes, never()).save(any());
  }

  @Test
  void create_invalidPassType_isRefusedAndStoresNothing() {
    assertThatThrownBy(() ->
            useCase.create(
                new CreatePassTypeCommand(
                    "ZERO", "No days", 0, Money.of("5"), Set.of(VehicleType.E_BIKE))))
        .isInstanceOf(IllegalArgumentException.class);
    verify(passTypes, never()).save(any());
  }

  // ------------------------------------------------------------- update

  @Test
  void update_changesOnlyWhatIsGiven() {
    when(passTypes.findById(DAY_ID)).thenReturn(Optional.of(DAY));
    when(passTypes.save(any(PassType.class))).thenAnswer(call -> call.getArgument(0));

    PassType updated =
        useCase.update(DAY_ID, new UpdatePassTypeCommand(null, null, Money.of("12.50"), null));

    assertThat(updated.basePrice()).isEqualTo(Money.of("12.50"));
    assertThat(updated.name()).isEqualTo("Day pass");
    assertThat(updated.validityDays()).isEqualTo(1);
    assertThat(updated.vehicleTypes())
        .containsExactlyInAnyOrder(VehicleType.E_BIKE, VehicleType.E_SCOOTER);
    assertThat(updated.id()).isEqualTo(DAY_ID);
    assertThat(updated.code()).isEqualTo("DAY");
    assertThat(saved()).isEqualTo(updated);
  }

  @Test
  void update_everyField_isApplied() {
    when(passTypes.findById(DAY_ID)).thenReturn(Optional.of(DAY));
    when(passTypes.save(any(PassType.class))).thenAnswer(call -> call.getArgument(0));

    PassType updated =
        useCase.update(
            DAY_ID,
            new UpdatePassTypeCommand(
                "Two days", 2, Money.of("18"), Set.of(VehicleType.E_SCOOTER)));

    assertThat(updated.name()).isEqualTo("Two days");
    assertThat(updated.validityDays()).isEqualTo(2);
    assertThat(updated.basePrice()).isEqualTo(Money.of("18.00"));
    assertThat(updated.vehicleTypes()).containsExactly(VehicleType.E_SCOOTER);
  }

  @Test
  void update_unknownPassType_isNotFound() {
    when(passTypes.findById(DAY_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> useCase.update(DAY_ID, new UpdatePassTypeCommand("x", null, null, null)))
        .isInstanceOf(PassTypeNotFoundException.class);
    verify(passTypes, never()).save(any());
  }

  @Test
  void update_invalidValue_isRefusedAndStoresNothing() {
    when(passTypes.findById(DAY_ID)).thenReturn(Optional.of(DAY));

    assertThatThrownBy(() -> useCase.update(DAY_ID, new UpdatePassTypeCommand(null, 0, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> useCase.update(DAY_ID, new UpdatePassTypeCommand(null, null, null, Set.of())))
        .isInstanceOf(IllegalArgumentException.class);
    verify(passTypes, never()).save(any());
  }

  @Test
  void list_isEveryPassType() {
    when(passTypes.findAll()).thenReturn(List.of(DAY));

    assertThat(useCase.list()).containsExactly(DAY);
  }
}
