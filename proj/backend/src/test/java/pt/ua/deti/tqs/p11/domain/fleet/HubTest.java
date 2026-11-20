package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Hub data and the capacity rules of BR14, at the boundaries given in the Product Specs. */
class HubTest {

  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final FleetCode CODE = FleetCode.of("hub-03");
  private static final GeoPosition CANAL_CENTRAL = new GeoPosition(40.6405, -8.6538);

  private static Hub withCapacity(int capacity) {
    return new Hub(ID, CODE, "Canal Central", CANAL_CENTRAL, capacity);
  }

  // ------------------------------------------------------------- registration data

  @Test
  void new_validHub_keepsItsData() {
    Hub hub = withCapacity(10);

    assertThat(hub.id()).isEqualTo(ID);
    assertThat(hub.code()).isEqualTo(CODE);
    assertThat(hub.name()).isEqualTo("Canal Central");
    assertThat(hub.position()).isEqualTo(CANAL_CENTRAL);
    assertThat(hub.capacity()).isEqualTo(10);
  }

  @Test
  void new_nameIsTrimmed() {
    assertThat(new Hub(ID, CODE, "  Canal Central ", CANAL_CENTRAL, 10).name())
        .isEqualTo("Canal Central");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void new_blankName_isRejected(String name) {
    assertThatThrownBy(() -> new Hub(ID, CODE, name, CANAL_CENTRAL, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_capacityOfOne_isAccepted() {
    assertThat(withCapacity(1).capacity()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void new_capacityBelowOne_isRejected(int capacity) {
    assertThatThrownBy(() -> withCapacity(capacity)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_missingField_isRejected() {
    assertThatThrownBy(() -> new Hub(null, CODE, "Canal Central", CANAL_CENTRAL, 10))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Hub(ID, null, "Canal Central", CANAL_CENTRAL, 10))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Hub(ID, CODE, null, CANAL_CENTRAL, 10))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Hub(ID, CODE, "Canal Central", null, 10))
        .isInstanceOf(NullPointerException.class);
  }

  // ------------------------------------------------------------- free slots (BR14)

  @Test
  void hasFreeSlot_capacity10With9Held_isTrue() {
    assertThat(withCapacity(10).hasFreeSlot(9)).isTrue();
  }

  @Test
  void hasFreeSlot_capacity10With10Held_isFalse() {
    assertThat(withCapacity(10).hasFreeSlot(10)).isFalse();
  }

  @Test
  void requireFreeSlot_capacity10With9Held_passes() {
    assertThatCode(() -> withCapacity(10).requireFreeSlot(9)).doesNotThrowAnyException();
  }

  @Test
  void requireFreeSlot_capacity10With10Held_isRefusedAsFull() {
    assertThatThrownBy(() -> withCapacity(10).requireFreeSlot(10))
        .isInstanceOf(HubFullException.class)
        .hasMessageContaining("hub-03");
  }

  @Test
  void hasFreeSlot_negativeHeld_isRejected() {
    assertThatThrownBy(() -> withCapacity(10).hasFreeSlot(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ------------------------------------------------------------- changing capacity (BR14)

  @Test
  void withCapacity_lowering10To9With9Held_isAccepted() {
    Hub hub = withCapacity(10).withCapacity(9, 9);

    assertThat(hub.capacity()).isEqualTo(9);
    assertThat(hub.code()).isEqualTo(CODE);
  }

  @Test
  void withCapacity_lowering10To9With10Held_isRefused() {
    assertThatThrownBy(() -> withCapacity(10).withCapacity(9, 10))
        .isInstanceOf(CapacityBelowVehiclesHeldException.class)
        .hasMessageContaining("hub-03");
  }

  @Test
  void withCapacity_raisingAFullHub_isAccepted() {
    assertThat(withCapacity(10).withCapacity(12, 10).capacity()).isEqualTo(12);
  }

  @Test
  void withCapacity_belowOneOnAnEmptyHub_isRejected() {
    assertThatThrownBy(() -> withCapacity(10).withCapacity(0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
