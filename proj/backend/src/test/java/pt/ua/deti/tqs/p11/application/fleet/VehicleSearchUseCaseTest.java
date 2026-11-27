package pt.ua.deti.tqs.p11.application.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryRule;
import pt.ua.deti.tqs.p11.domain.fleet.Bookability;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * Searching the vehicles of a hub (US02). Whether one vehicle is bookable is tested in
 * VehicleBookabilityTest; here, that the search asks the right hub, judges every vehicle it holds
 * against the configured minimum, filters by type and refuses an unknown hub.
 */
@ExtendWith(MockitoExtension.class)
class VehicleSearchUseCaseTest {

  private static final UUID HUB_03_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final Hub HUB_03 =
      new Hub(
          HUB_03_ID,
          FleetCode.of("hub-03"),
          "Canal Central",
          new GeoPosition(40.6405, -8.6538),
          10);

  @Mock private HubRepository hubs;
  @Mock private VehicleRepository vehicles;

  private VehicleSearchUseCase useCase;

  private static BatteryRule rule(int floor) {
    return new BatteryRule(
        BatteryLevel.of(floor), Map.of(VehicleType.E_BIKE, 5, VehicleType.E_SCOOTER, 8));
  }

  @BeforeEach
  void setUp() {
    useCase = new VehicleSearchUseCase(hubs, vehicles, () -> rule(20));
  }

  private static Vehicle vehicle(
      String code, VehicleType type, VehicleState state, Integer batteryPercent) {
    UUID id = UUID.nameUUIDFromBytes(code.getBytes(StandardCharsets.UTF_8));
    Vehicle vehicle = new Vehicle(id, FleetCode.of(code), type, state, HUB_03_ID);
    return batteryPercent == null ? vehicle : vehicle.withBattery(BatteryLevel.of(batteryPercent));
  }

  private void hubHolds(Vehicle... held) {
    when(hubs.findById(HUB_03_ID)).thenReturn(Optional.of(HUB_03));
    when(vehicles.findAtHub(HUB_03_ID)).thenReturn(List.of(held));
  }

  @Test
  void search_unknownHub_isRefusedAndNoVehicleIsRead() {
    when(hubs.findById(HUB_03_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.search(HUB_03_ID)).isInstanceOf(HubNotFoundException.class);
    verifyNoInteractions(vehicles);
  }

  @Test
  void search_hubWithoutVehicles_returnsAnEmptyList() {
    hubHolds();

    assertThat(useCase.search(HUB_03_ID)).isEmpty();
  }

  @Test
  void search_returnsEveryVehicleTheHubHoldsWithItsBookability() {
    Vehicle ready = vehicle("bike-0001", VehicleType.E_BIKE, VehicleState.AVAILABLE, 80);
    Vehicle low = vehicle("bike-0002", VehicleType.E_BIKE, VehicleState.AVAILABLE, 19);
    Vehicle reserved = vehicle("bike-0003", VehicleType.E_BIKE, VehicleState.RESERVED, 90);
    Vehicle noReading = vehicle("bike-0004", VehicleType.E_BIKE, VehicleState.AVAILABLE, null);
    hubHolds(ready, low, reserved, noReading);

    List<SearchedVehicle> result = useCase.search(HUB_03_ID);

    assertThat(result)
        .extracting(SearchedVehicle::vehicle, SearchedVehicle::bookability)
        .containsExactly(
            tuple(ready, Bookability.BOOKABLE),
            tuple(low, Bookability.LOW_BATTERY),
            tuple(reserved, Bookability.NOT_AVAILABLE),
            tuple(noReading, Bookability.NO_BATTERY_READING));
  }

  /** The configured minimum is used, not a fixed 20%: 19% passes a minimum of 15%. */
  @Test
  void search_usesTheConfiguredMinimum() {
    VehicleSearchUseCase lenient = new VehicleSearchUseCase(hubs, vehicles, () -> rule(15));
    hubHolds(vehicle("bike-0002", VehicleType.E_BIKE, VehicleState.AVAILABLE, 19));

    assertThat(lenient.search(HUB_03_ID))
        .extracting(SearchedVehicle::bookability)
        .containsExactly(Bookability.BOOKABLE);
  }

  @Test
  void search_withType_keepsOnlyVehiclesOfThatType() {
    Vehicle bike = vehicle("bike-0001", VehicleType.E_BIKE, VehicleState.AVAILABLE, 80);
    Vehicle scooter = vehicle("scooter-0001", VehicleType.E_SCOOTER, VehicleState.AVAILABLE, 80);
    hubHolds(bike, scooter);

    assertThat(useCase.search(HUB_03_ID, VehicleType.E_SCOOTER))
        .extracting(SearchedVehicle::vehicle)
        .containsExactly(scooter);
    assertThat(useCase.search(HUB_03_ID, VehicleType.E_BIKE))
        .extracting(SearchedVehicle::vehicle)
        .containsExactly(bike);
  }

  @Test
  void search_withTypeNoVehicleHas_returnsAnEmptyList() {
    hubHolds(vehicle("bike-0001", VehicleType.E_BIKE, VehicleState.AVAILABLE, 80));

    assertThat(useCase.search(HUB_03_ID, VehicleType.E_SCOOTER)).isEmpty();
  }

  /** US14: the rule is read at every search, so a change by the client manager is seen at once. */
  @Test
  void search_usesTheRuleInForceAtEachSearch() {
    AtomicReference<BatteryRule> inForce = new AtomicReference<>(rule(20));
    VehicleSearchUseCase changing = new VehicleSearchUseCase(hubs, vehicles, inForce::get);
    hubHolds(vehicle("bike-0001", VehicleType.E_BIKE, VehicleState.AVAILABLE, 40));

    assertThat(changing.search(HUB_03_ID))
        .extracting(SearchedVehicle::bookability)
        .containsExactly(Bookability.BOOKABLE);

    inForce.set(rule(50));

    assertThat(changing.search(HUB_03_ID))
        .extracting(SearchedVehicle::bookability)
        .containsExactly(Bookability.LOW_BATTERY);
  }

  // ------------------------------------------------------------- duration of the ride

  @Test
  void search_withoutADuration_requiresTheFloor() {
    hubHolds(vehicle("bike-0001", VehicleType.E_BIKE, VehicleState.AVAILABLE, 80));

    assertThat(useCase.search(HUB_03_ID))
        .extracting(SearchedVehicle::requiredPercent)
        .containsExactly(20);
  }

  @Test
  void search_withADuration_judgesEachVehicleAgainstTheBatteryItsTypeNeeds() {
    Vehicle bike = vehicle("bike-0001", VehicleType.E_BIKE, VehicleState.AVAILABLE, 30);
    Vehicle scooter = vehicle("scooter-0001", VehicleType.E_SCOOTER, VehicleState.AVAILABLE, 30);
    hubHolds(bike, scooter);

    List<SearchedVehicle> result = useCase.search(HUB_03_ID, null, 60);

    // 60 minutes = two blocks: a bike needs 20 + 2 x 5 = 30%, a scooter 20 + 2 x 8 = 36%.
    assertThat(result)
        .extracting(
            SearchedVehicle::vehicle,
            SearchedVehicle::requiredPercent,
            SearchedVehicle::bookability)
        .containsExactly(
            tuple(bike, 30, Bookability.BOOKABLE),
            tuple(scooter, 36, Bookability.INSUFFICIENT_FOR_DURATION));
  }

  @Test
  void search_withATypeAndADuration_appliesBoth() {
    Vehicle bike = vehicle("bike-0001", VehicleType.E_BIKE, VehicleState.AVAILABLE, 30);
    Vehicle scooter = vehicle("scooter-0001", VehicleType.E_SCOOTER, VehicleState.AVAILABLE, 99);
    hubHolds(bike, scooter);

    assertThat(useCase.search(HUB_03_ID, VehicleType.E_SCOOTER, 60))
        .extracting(SearchedVehicle::vehicle, SearchedVehicle::bookability)
        .containsExactly(tuple(scooter, Bookability.BOOKABLE));
  }

  @Test
  void search_durationOutsideTheLimits_isRefusedBeforeAnyVehicleIsRead() {
    assertThatThrownBy(() -> useCase.search(HUB_03_ID, null, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.search(HUB_03_ID, null, BatteryRule.MAX_MINUTES + 1))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(vehicles);
  }
}
