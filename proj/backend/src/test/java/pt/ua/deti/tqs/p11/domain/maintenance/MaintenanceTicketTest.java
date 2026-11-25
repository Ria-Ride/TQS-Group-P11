package pt.ua.deti.tqs.p11.domain.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * A maintenance ticket from opening to assignment (US10). BR19: the SLA deadline is the opening
 * time plus the contracted repair time. BR20: only an active OPERATOR who is a field technician can
 * be assigned. Starting, closing and breaching a ticket belong to US11.
 */
class MaintenanceTicketTest {

  private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-000000000009");
  private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID TIAGO = UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final UUID RITA = UUID.fromString("00000000-0000-0000-0000-000000000302");
  private static final FaultCode BRAKE_WEAR = FaultCode.of("BRAKE_WEAR");
  private static final Instant OPENED_AT = Instant.parse("2026-11-10T10:00:00Z");
  private static final Duration SLA = Duration.ofHours(24);

  private static MaintenanceTicket fromFault() {
    return MaintenanceTicket.openFromFault(TICKET, VEHICLE, BRAKE_WEAR, OPENED_AT, SLA);
  }

  private static User account(UUID id, Role role, boolean fieldTechnician) {
    return User.account(
        id,
        "Tiago Lopes",
        new EmailAddress(id + "@example.com"),
        "hash",
        role,
        fieldTechnician);
  }

  private static User technician(UUID id) {
    return account(id, Role.OPERATOR, true);
  }

  private static MaintenanceTicket inState(TicketState state, UUID technicianId) {
    return new MaintenanceTicket(
        TICKET,
        VEHICLE,
        BRAKE_WEAR,
        null,
        state,
        OPENED_AT,
        OPENED_AT.plus(SLA),
        null,
        technicianId);
  }

  // ------------------------------------------------------------- opening

  @Test
  void openFromFault_isOpenWithNoTechnicianAndNoOperator() {
    MaintenanceTicket ticket = fromFault();

    assertThat(ticket.id()).isEqualTo(TICKET);
    assertThat(ticket.vehicleId()).isEqualTo(VEHICLE);
    assertThat(ticket.faultCode()).isEqualTo(BRAKE_WEAR);
    assertThat(ticket.state()).isEqualTo(TicketState.OPEN);
    assertThat(ticket.openedAt()).isEqualTo(OPENED_AT);
    assertThat(ticket.assignedTechnician()).isEmpty();
    assertThat(ticket.openedByOperator()).isEmpty();
    assertThat(ticket.isAutomatic()).isTrue();
  }

  /** BR19: 10:00 on the 10th plus 24 h is exactly 10:00 on the 11th. */
  @Test
  void openFromFault_deadlineIsOpeningTimePlusTheSla() {
    assertThat(fromFault().slaDeadline()).isEqualTo(Instant.parse("2026-11-11T10:00:00Z"));
  }

  @Test
  void openFromFault_otherSla_movesTheDeadline() {
    MaintenanceTicket ticket =
        MaintenanceTicket.openFromFault(TICKET, VEHICLE, BRAKE_WEAR, OPENED_AT, Duration.ofHours(8));

    assertThat(ticket.slaDeadline()).isEqualTo(Instant.parse("2026-11-10T18:00:00Z"));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void openFromFault_slaNotPositive_isRejected(long minutes) {
    Duration sla = Duration.ofMinutes(minutes);

    assertThatThrownBy(
            () -> MaintenanceTicket.openFromFault(TICKET, VEHICLE, BRAKE_WEAR, OPENED_AT, sla))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void openManually_recordsTheOperatorAndTheDescription() {
    MaintenanceTicket ticket =
        MaintenanceTicket.openManually(
            TICKET, VEHICLE, BRAKE_WEAR, "  Brakes squeak  ", OPERATOR, OPENED_AT, SLA);

    assertThat(ticket.state()).isEqualTo(TicketState.OPEN);
    assertThat(ticket.description()).isEqualTo("Brakes squeak");
    assertThat(ticket.openedByOperator()).contains(OPERATOR);
    assertThat(ticket.isAutomatic()).isFalse();
    assertThat(ticket.slaDeadline()).isEqualTo(Instant.parse("2026-11-11T10:00:00Z"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void openManually_blankDescription_isRejected(String description) {
    assertThatThrownBy(
            () ->
                MaintenanceTicket.openManually(
                    TICKET, VEHICLE, BRAKE_WEAR, description, OPERATOR, OPENED_AT, SLA))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void openManually_withoutOperatorOrDescription_isRejected() {
    assertThatThrownBy(
            () ->
                MaintenanceTicket.openManually(
                    TICKET, VEHICLE, BRAKE_WEAR, null, OPERATOR, OPENED_AT, SLA))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                MaintenanceTicket.openManually(
                    TICKET, VEHICLE, BRAKE_WEAR, "Brakes squeak", null, OPENED_AT, SLA))
        .isInstanceOf(NullPointerException.class);
  }

  // ------------------------------------------------------------- assignment

  @Test
  void assignTo_fieldTechnician_isAssigned() {
    MaintenanceTicket ticket = fromFault().assignTo(technician(TIAGO));

    assertThat(ticket.state()).isEqualTo(TicketState.ASSIGNED);
    assertThat(ticket.assignedTechnician()).contains(TIAGO);
  }

  @Test
  void assignTo_keepsEverythingElse() {
    MaintenanceTicket open = fromFault();

    MaintenanceTicket assigned = open.assignTo(technician(TIAGO));

    assertThat(assigned.id()).isEqualTo(open.id());
    assertThat(assigned.vehicleId()).isEqualTo(open.vehicleId());
    assertThat(assigned.faultCode()).isEqualTo(open.faultCode());
    assertThat(assigned.openedAt()).isEqualTo(open.openedAt());
    assertThat(assigned.slaDeadline()).isEqualTo(open.slaDeadline());
  }

  /** A ticket can be handed to another technician before the work starts. */
  @Test
  void assignTo_assignedTicket_changesTheTechnician() {
    MaintenanceTicket ticket = fromFault().assignTo(technician(TIAGO)).assignTo(technician(RITA));

    assertThat(ticket.state()).isEqualTo(TicketState.ASSIGNED);
    assertThat(ticket.assignedTechnician()).contains(RITA);
  }

  static Stream<User> notFieldTechnicians() {
    return Stream.of(
        account(RITA, Role.OPERATOR, false),
        technician(RITA).deactivate(),
        account(RITA, Role.CLIENT_MANAGER, false),
        account(RITA, Role.END_USER, false),
        account(RITA, Role.ADMIN, false));
  }

  /** BR20: an operator without the flag, a deactivated technician and every other role. */
  @ParameterizedTest
  @MethodSource("notFieldTechnicians")
  void assignTo_notAnActiveFieldTechnician_isRefused(User user) {
    MaintenanceTicket open = fromFault();

    assertThatThrownBy(() -> open.assignTo(user))
        .isInstanceOfSatisfying(
            NotAFieldTechnicianException.class, e -> assertThat(e.userId()).isEqualTo(RITA));
  }

  /** Work has started or the ticket is finished: assignment is no longer possible. */
  @ParameterizedTest
  @EnumSource(names = {"IN_PROGRESS", "CLOSED", "CANCELLED"})
  void assignTo_ticketPastAssignment_isRefused(TicketState state) {
    MaintenanceTicket ticket = inState(state, TIAGO);

    assertThatThrownBy(() -> ticket.assignTo(technician(RITA)))
        .isInstanceOfSatisfying(
            ForbiddenTicketTransitionException.class,
            e -> assertThat(e.from()).isEqualTo(state));
  }

  // ------------------------------------------------------------- invariants

  @Test
  void new_deadlineNotAfterOpening_isRejected() {
    assertThatThrownBy(
            () ->
                new MaintenanceTicket(
                    TICKET, VEHICLE, BRAKE_WEAR, null, TicketState.OPEN, OPENED_AT, OPENED_AT,
                    null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_openTicketWithATechnician_isRejected() {
    assertThatThrownBy(() -> inState(TicketState.OPEN, TIAGO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @EnumSource(names = {"ASSIGNED", "IN_PROGRESS", "CLOSED"})
  void new_assignedOrLaterWithoutATechnician_isRejected(TicketState state) {
    assertThatThrownBy(() -> inState(state, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_missingField_isRejected() {
    Instant deadline = OPENED_AT.plus(SLA);
    TicketState open = TicketState.OPEN;

    assertThatThrownBy(
            () ->
                new MaintenanceTicket(
                    null, VEHICLE, BRAKE_WEAR, null, open, OPENED_AT, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceTicket(
                    TICKET, null, BRAKE_WEAR, null, open, OPENED_AT, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceTicket(
                    TICKET, VEHICLE, null, null, open, OPENED_AT, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceTicket(
                    TICKET, VEHICLE, BRAKE_WEAR, null, null, OPENED_AT, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceTicket(
                    TICKET, VEHICLE, BRAKE_WEAR, null, open, null, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceTicket(
                    TICKET, VEHICLE, BRAKE_WEAR, null, open, OPENED_AT, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
