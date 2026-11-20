package pt.ua.deti.tqs.p11.application.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.DirectTransactionRunner;
import pt.ua.deti.tqs.p11.domain.fleet.CapacityBelowVehiclesHeldException;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;

/** Hub use cases (US08, BR14). The capacity rule itself is tested in HubTest. */
@ExtendWith(MockitoExtension.class)
class HubManagementUseCaseTest {

  private static final UUID NEW_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID HUB_05_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000105");
  private static final FleetCode HUB_03_CODE = FleetCode.of("hub-03");
  private static final GeoPosition CANAL_CENTRAL = new GeoPosition(40.6405, -8.6538);
  private static final Hub HUB_03 = new Hub(NEW_ID, HUB_03_CODE, "Canal Central", CANAL_CENTRAL, 10);
  private static final Hub HUB_05 =
      new Hub(
          HUB_05_ID,
          FleetCode.of("hub-05"),
          "Hotel Ria Azul",
          new GeoPosition(40.6421, -8.6524),
          12);

  @Mock private HubRepository hubs;
  @Mock private VehicleRepository vehicles;

  private HubManagementUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new HubManagementUseCase(hubs, vehicles, new DirectTransactionRunner(), () -> NEW_ID);
  }

  private static RegisterHubCommand hub03(int capacity) {
    return new RegisterHubCommand("hub-03", "Canal Central", 40.6405, -8.6538, capacity);
  }

  @Test
  void register_newCode_savesTheHubWithAGeneratedIdAndNoVehicles() {
    when(hubs.existsByCode(HUB_03_CODE)).thenReturn(false);
    when(hubs.save(any(Hub.class))).then(returnsFirstArg());

    assertThat(useCase.register(hub03(10))).isEqualTo(new HubOccupancy(HUB_03, 0));
  }

  @Test
  void register_codeAlreadyInUse_isRefusedAndNothingIsSaved() {
    when(hubs.existsByCode(HUB_03_CODE)).thenReturn(true);

    assertThatThrownBy(() -> useCase.register(hub03(10)))
        .isInstanceOf(HubCodeAlreadyInUseException.class);
    verify(hubs, never()).save(any());
  }

  @Test
  void register_invalidData_isRefusedAndNothingIsSaved() {
    assertThatThrownBy(() -> useCase.register(hub03(0)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(hubs, never()).save(any());
  }

  @Test
  void changeCapacity_lowering10To9With9Held_savesTheNewCapacity() {
    when(hubs.findByIdForUpdate(NEW_ID)).thenReturn(Optional.of(HUB_03));
    when(vehicles.countAtHub(NEW_ID)).thenReturn(9);
    when(hubs.save(any(Hub.class))).then(returnsFirstArg());

    assertThat(useCase.changeCapacity(NEW_ID, 9))
        .isEqualTo(new HubOccupancy(HUB_03.withCapacity(9, 9), 9));
  }

  @Test
  void changeCapacity_lowering10To9With10Held_isRefusedAndNothingIsSaved() {
    when(hubs.findByIdForUpdate(NEW_ID)).thenReturn(Optional.of(HUB_03));
    when(vehicles.countAtHub(NEW_ID)).thenReturn(10);

    assertThatThrownBy(() -> useCase.changeCapacity(NEW_ID, 9))
        .isInstanceOf(CapacityBelowVehiclesHeldException.class);
    verify(hubs, never()).save(any());
  }

  @Test
  void changeCapacity_unknownHub_isNotFound() {
    when(hubs.findByIdForUpdate(NEW_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.changeCapacity(NEW_ID, 9))
        .isInstanceOf(HubNotFoundException.class);
  }

  @Test
  void list_returnsEveryHubWithTheVehiclesItHolds() {
    when(hubs.findAll()).thenReturn(List.of(HUB_03, HUB_05));
    when(vehicles.countAtHub(NEW_ID)).thenReturn(7);
    when(vehicles.countAtHub(HUB_05_ID)).thenReturn(0);

    assertThat(useCase.list())
        .containsExactly(new HubOccupancy(HUB_03, 7), new HubOccupancy(HUB_05, 0));
  }

  @Test
  void get_returnsTheHubWithTheVehiclesItHolds() {
    when(hubs.findById(NEW_ID)).thenReturn(Optional.of(HUB_03));
    when(vehicles.countAtHub(NEW_ID)).thenReturn(3);

    assertThat(useCase.get(NEW_ID)).isEqualTo(new HubOccupancy(HUB_03, 3));
  }

  @Test
  void get_unknownHub_isNotFound() {
    when(hubs.findById(NEW_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.get(NEW_ID)).isInstanceOf(HubNotFoundException.class);
  }
}
