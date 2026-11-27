package pt.ua.deti.tqs.p11.application.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.DirectTransactionRunner;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesChange;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;
import pt.ua.deti.tqs.p11.domain.rules.VehicleTypeRules;

/**
 * What the client manager does with the rules of use (US14, BR21): read the current version, change
 * it into a new one, change a deposit, read the history. Which values are valid is tested in
 * RulesOfUseTest; here, that the use case stores a new version for a valid change and nothing for
 * an invalid one, and never touches the old version.
 */
@ExtendWith(MockitoExtension.class)
class RulesOfUseUseCaseTest {

  private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");
  private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
  private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000066");

  @Mock private RulesOfUseRepository rules;

  private RulesOfUseUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new RulesOfUseUseCase(
            rules, new DirectTransactionRunner(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static RulesOfUse version(int number) {
    return new RulesOfUse(
        number,
        T0,
        null,
        Duration.ofHours(8),
        BatteryLevel.of(20),
        Duration.ofMinutes(15),
        Duration.ofMinutes(10),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        Money.of("1.50"),
        Map.of(
            VehicleType.E_BIKE, new VehicleTypeRules(Money.of("20"), 5),
            VehicleType.E_SCOOTER, new VehicleTypeRules(Money.of("30"), 8)));
  }

  private static RulesChange change(Duration maxBooking) {
    return new RulesChange(
        maxBooking,
        BatteryLevel.of(25),
        Duration.ofMinutes(20),
        Duration.ofMinutes(12),
        Duration.ZERO,
        Duration.ofMinutes(30),
        Money.of("2"),
        Map.of(VehicleType.E_BIKE, 4, VehicleType.E_SCOOTER, 9));
  }

  private RulesOfUse savedVersion() {
    ArgumentCaptor<RulesOfUse> saved = ArgumentCaptor.forClass(RulesOfUse.class);
    verify(rules).save(saved.capture());
    return saved.getValue();
  }

  @Test
  void current_isTheLatestVersion() {
    when(rules.findCurrent()).thenReturn(Optional.of(version(3)));

    assertThat(useCase.current().version()).isEqualTo(3);
  }

  @Test
  void current_noVersionStored_isAnError() {
    when(rules.findCurrent()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.current()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void change_storesTheNextVersionWithTheTimeAndWhoMadeIt() {
    when(rules.findCurrent()).thenReturn(Optional.of(version(1)));
    when(rules.save(any(RulesOfUse.class))).thenAnswer(call -> call.getArgument(0));

    RulesOfUse next = useCase.change(change(Duration.ofHours(4)), MANAGER);

    RulesOfUse saved = savedVersion();
    assertThat(saved.version()).isEqualTo(2);
    assertThat(saved.createdAt()).isEqualTo(NOW);
    assertThat(saved.createdBy()).isEqualTo(MANAGER);
    assertThat(saved.maxBookingDuration()).isEqualTo(Duration.ofHours(4));
    assertThat(next).isEqualTo(saved);
  }

  @Test
  void change_invalidValue_storesNothing() {
    when(rules.findCurrent()).thenReturn(Optional.of(version(1)));

    assertThatThrownBy(() -> useCase.change(change(Duration.ofHours(25)), MANAGER))
        .isInstanceOf(IllegalArgumentException.class);
    verify(rules, never()).save(any());
  }

  @Test
  void setDeposit_storesTheNextVersionWithOneDepositChanged() {
    when(rules.findCurrent()).thenReturn(Optional.of(version(4)));
    when(rules.save(any(RulesOfUse.class))).thenAnswer(call -> call.getArgument(0));

    useCase.setDeposit(VehicleType.E_SCOOTER, Money.of("45"), MANAGER);

    RulesOfUse saved = savedVersion();
    assertThat(saved.version()).isEqualTo(5);
    assertThat(saved.createdBy()).isEqualTo(MANAGER);
    assertThat(saved.deposit(VehicleType.E_SCOOTER)).isEqualTo(Money.of("45.00"));
    assertThat(saved.deposit(VehicleType.E_BIKE)).isEqualTo(Money.of("20.00"));
  }

  @Test
  void history_isWhatTheRepositoryHolds_newestFirst() {
    when(rules.findAll()).thenReturn(List.of(version(2), version(1)));

    assertThat(useCase.history()).extracting(RulesOfUse::version).containsExactly(2, 1);
  }

  /**
   * Two managers saving at once: the second finds the version taken and is told, not overwritten.
   */
  @Test
  void change_versionAlreadyTaken_isReportedAsAConflict() {
    when(rules.findCurrent()).thenReturn(Optional.of(version(1)));
    when(rules.save(any(RulesOfUse.class))).thenThrow(new RulesVersionConflictException(2));

    assertThatThrownBy(() -> useCase.change(change(Duration.ofHours(4)), MANAGER))
        .isInstanceOf(RulesVersionConflictException.class);
  }
}
