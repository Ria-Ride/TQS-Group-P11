package pt.ua.deti.tqs.p11.domain.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * A maintenance ticket from opening to closing (US10, US11). BR19: the SLA deadline is the opening
 * time plus the contracted repair time, and a ticket closed after it is breached. BR20: only an
 * active OPERATOR who is a field technician can be assigned, and only that technician can start and
 * close the ticket, recording the work done.
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
        id, "Tiago Lopes", new EmailAddress(id + "@example.com"), "hash", role, fieldTechnician);
  }

  private static User technician(UUID id) {
    return account(id, Role.OPERATOR, true);
  }

  /** A Closed ticket comes with its closing time and work done, as US11 requires. */
  private static MaintenanceTicket inState(TicketState state, UUID technicianId) {
    boolean closed = state == TicketState.CLOSED;
    return new MaintenanceTicket(
        TICKET,
        VEHICLE,
        BRAKE_WEAR,
        null,
        state,
        OPENED_AT,
        OPENED_AT.plus(SLA),
        null,
        technicianId,
        closed ? OPENED_AT.plus(Duration.ofHours(2)) : null,
        closed ? "Replaced brake pads" : null);
  }

  private static MaintenanceTicket inProgress() {
    return fromFault().assignTo(technician(TIAGO)).start(TIAGO);
  }

  private static MaintenanceTicket closedAfter(Duration repair) {
    return inProgress().close(TIAGO, "Replaced brake pads", OPENED_AT.plus(repair));
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
        MaintenanceTicket.openFromFault(
            TICKET, VEHICLE, BRAKE_WEAR, OPENED_AT, Duration.ofHours(8));

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
    assertThatThrownBy(() ->
            MaintenanceTicket.openManually(
                TICKET, VEHICLE, BRAKE_WEAR, description, OPERATOR, OPENED_AT, SLA))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void openManually_withoutOperatorOrDescription_isRejected() {
    assertThatThrownBy(() ->
            MaintenanceTicket.openManually(
                TICKET, VEHICLE, BRAKE_WEAR, null, OPERATOR, OPENED_AT, SLA))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
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
            ForbiddenTicketTransitionException.class, e -> assertThat(e.from()).isEqualTo(state));
  }

  // ------------------------------------------------------------- invariants

  @Test
  void new_deadlineNotAfterOpening_isRejected() {
    assertThatThrownBy(() ->
            new MaintenanceTicket(
                TICKET,
                VEHICLE,
                BRAKE_WEAR,
                null,
                TicketState.OPEN,
                OPENED_AT,
                OPENED_AT,
                null,
                null))
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

    assertThatThrownBy(() ->
            new MaintenanceTicket(
                null, VEHICLE, BRAKE_WEAR, null, open, OPENED_AT, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new MaintenanceTicket(
                TICKET, null, BRAKE_WEAR, null, open, OPENED_AT, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new MaintenanceTicket(
                TICKET, VEHICLE, null, null, open, OPENED_AT, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new MaintenanceTicket(
                TICKET, VEHICLE, BRAKE_WEAR, null, null, OPENED_AT, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new MaintenanceTicket(
                TICKET, VEHICLE, BRAKE_WEAR, null, open, null, deadline, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new MaintenanceTicket(
                TICKET, VEHICLE, BRAKE_WEAR, null, open, OPENED_AT, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  // ------------------------------------------------------------- starting the work (US11)

  @Test
  void start_assignedTicketByItsTechnician_isInProgressWithTheSameTechnician() {
    MaintenanceTicket started = fromFault().assignTo(technician(TIAGO)).start(TIAGO);

    assertThat(started.state()).isEqualTo(TicketState.IN_PROGRESS);
    assertThat(started.assignedTechnician()).contains(TIAGO);
    assertThat(started.slaDeadline()).isEqualTo(OPENED_AT.plus(SLA));
  }

  @Test
  void start_byAnotherTechnician_isRefused() {
    MaintenanceTicket assigned = fromFault().assignTo(technician(TIAGO));

    assertThatThrownBy(() -> assigned.start(RITA))
        .isInstanceOfSatisfying(
            NotTheAssignedTechnicianException.class,
            e -> {
              assertThat(e.ticketId()).isEqualTo(TICKET);
              assertThat(e.userId()).isEqualTo(RITA);
            });
  }

  @Test
  void start_openTicket_isRefusedBecauseNobodyIsAssigned() {
    MaintenanceTicket open = fromFault();

    assertThatThrownBy(() -> open.start(TIAGO))
        .isInstanceOfSatisfying(
            ForbiddenTicketTransitionException.class,
            e -> assertThat(e.from()).isEqualTo(TicketState.OPEN));
  }

  @ParameterizedTest
  @EnumSource(names = {"IN_PROGRESS", "CLOSED", "CANCELLED"})
  void start_ticketPastAssignment_isRefused(TicketState state) {
    MaintenanceTicket ticket = inState(state, TIAGO);

    assertThatThrownBy(() -> ticket.start(TIAGO))
        .isInstanceOfSatisfying(
            ForbiddenTicketTransitionException.class, e -> assertThat(e.from()).isEqualTo(state));
  }

  // ------------------------------------------------------------- closing (BR20)

  @Test
  void close_inProgressTicketByItsTechnician_isClosedWithTheWorkAndTheTime() {
    Instant at = OPENED_AT.plus(Duration.ofHours(3));

    MaintenanceTicket closed = inProgress().close(TIAGO, "  Replaced brake pads  ", at);

    assertThat(closed.state()).isEqualTo(TicketState.CLOSED);
    assertThat(closed.workDone()).isEqualTo("Replaced brake pads");
    assertThat(closed.closedAt()).isEqualTo(at);
    assertThat(closed.assignedTechnician()).contains(TIAGO);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void close_blankWorkDone_isRefused(String workDone) {
    MaintenanceTicket ticket = inProgress();
    Instant at = OPENED_AT.plus(Duration.ofHours(3));

    assertThatThrownBy(() -> ticket.close(TIAGO, workDone, at))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void close_withoutWorkDone_isRefused() {
    MaintenanceTicket ticket = inProgress();
    Instant at = OPENED_AT.plus(Duration.ofHours(3));

    assertThatThrownBy(() -> ticket.close(TIAGO, null, at))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void close_byAnotherTechnician_isRefused() {
    MaintenanceTicket ticket = inProgress();
    Instant at = OPENED_AT.plus(Duration.ofHours(3));

    assertThatThrownBy(() -> ticket.close(RITA, "Replaced brake pads", at))
        .isInstanceOf(NotTheAssignedTechnicianException.class);
  }

  /** The criterion closes an InProgress ticket: the work must have been started first. */
  @ParameterizedTest
  @EnumSource(names = {"ASSIGNED", "CLOSED", "CANCELLED"})
  void close_ticketNotInProgress_isRefused(TicketState state) {
    MaintenanceTicket ticket = inState(state, TIAGO);
    Instant at = OPENED_AT.plus(Duration.ofHours(3));

    assertThatThrownBy(() -> ticket.close(TIAGO, "Replaced brake pads", at))
        .isInstanceOfSatisfying(
            ForbiddenTicketTransitionException.class, e -> assertThat(e.from()).isEqualTo(state));
  }

  @Test
  void close_beforeTheOpening_isRefused() {
    MaintenanceTicket ticket = inProgress();
    Instant before = OPENED_AT.minusSeconds(1);

    assertThatThrownBy(() -> ticket.close(TIAGO, "Replaced brake pads", before))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ------------------------------------------------------------- SLA and time to repair (BR19)

  /** BR19: closed 24 h 00 min after opening is on time; 24 h 01 min is breached. */
  @ParameterizedTest(name = "closed after {0} minutes: breached {1}")
  @CsvSource({"1439, false", "1440, false", "1441, true"})
  void isBreachedAt_closedTicket_dependsOnWhenItWasClosed(long minutes, boolean breached) {
    MaintenanceTicket closed = closedAfter(Duration.ofMinutes(minutes));

    // Long after the deadline, so only the closing time can decide.
    assertThat(closed.isBreachedAt(OPENED_AT.plus(Duration.ofDays(10)))).isEqualTo(breached);
  }

  /** An open ticket becomes breached once now is past its deadline, not at the deadline itself. */
  @ParameterizedTest(name = "open, {0} minutes after opening: breached {1}")
  @CsvSource({"1439, false", "1440, false", "1441, true"})
  void isBreachedAt_openTicket_dependsOnNow(long minutes, boolean breached) {
    MaintenanceTicket open = inProgress();

    assertThat(open.isBreachedAt(OPENED_AT.plus(Duration.ofMinutes(minutes)))).isEqualTo(breached);
  }

  /** A cancelled ticket needs no repair, so it is never late, however long ago it was due. */
  @Test
  void isBreachedAt_cancelledTicket_isNeverBreached() {
    MaintenanceTicket cancelled = inState(TicketState.CANCELLED, TIAGO);

    assertThat(cancelled.isBreachedAt(OPENED_AT.plus(Duration.ofDays(10)))).isFalse();
  }

  @Test
  void timeToRepair_closedTicket_isFromOpeningToClosing() {
    assertThat(closedAfter(Duration.ofMinutes(1441)).timeToRepair())
        .contains(Duration.ofMinutes(1441));
  }

  @Test
  void timeToRepair_ticketNotClosed_isEmpty() {
    assertThat(inProgress().timeToRepair()).isEmpty();
  }

  // ------------------------------------------------------------- invariants of closing

  @Test
  void new_closedTicketWithoutClosingTimeOrWork_isRejected() {
    assertThatThrownBy(() -> closedWith(null, "Replaced brake pads"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> closedWith(OPENED_AT.plusSeconds(60), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> closedWith(OPENED_AT.plusSeconds(60), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_closedBeforeOpening_isRejected() {
    assertThatThrownBy(() -> closedWith(OPENED_AT.minusSeconds(1), "Replaced brake pads"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @EnumSource(names = {"OPEN", "ASSIGNED", "IN_PROGRESS", "CANCELLED"})
  void new_ticketNotClosedWithClosingTimeOrWork_isRejected(TicketState state) {
    UUID technician = state == TicketState.OPEN ? null : TIAGO;
    Instant deadline = OPENED_AT.plus(SLA);

    assertThatThrownBy(() ->
            new MaintenanceTicket(
                TICKET,
                VEHICLE,
                BRAKE_WEAR,
                null,
                state,
                OPENED_AT,
                deadline,
                null,
                technician,
                OPENED_AT.plusSeconds(60),
                null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new MaintenanceTicket(
                TICKET,
                VEHICLE,
                BRAKE_WEAR,
                null,
                state,
                OPENED_AT,
                deadline,
                null,
                technician,
                null,
                "Replaced brake pads"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static MaintenanceTicket closedWith(Instant closedAt, String workDone) {
    return new MaintenanceTicket(
        TICKET,
        VEHICLE,
        BRAKE_WEAR,
        null,
        TicketState.CLOSED,
        OPENED_AT,
        OPENED_AT.plus(SLA),
        null,
        TIAGO,
        closedAt,
        workDone);
  }
}
