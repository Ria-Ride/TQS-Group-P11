package pt.ua.deti.tqs.p11.domain.maintenance;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * A repair job for one vehicle and one fault (US10, US11). Immutable: every change returns a new
 * ticket, and the constructor checks the rules, so an invalid ticket cannot exist. description is
 * null for a ticket opened from a fault reading; openedBy is the operator of a manual ticket, null
 * for one opened from a fault reading; technicianId is null until the ticket is assigned; closedAt
 * and workDone are set when, and only when, the ticket is Closed.
 */
public record MaintenanceTicket(
    UUID id,
    UUID vehicleId,
    FaultCode faultCode,
    String description,
    TicketState state,
    Instant openedAt,
    Instant slaDeadline,
    UUID openedBy,
    UUID technicianId,
    Instant closedAt,
    String workDone) {

  /** States in which someone is responsible for the work, so a technician must be set. */
  private static final Set<TicketState> WITH_TECHNICIAN =
      EnumSet.of(TicketState.ASSIGNED, TicketState.IN_PROGRESS, TicketState.CLOSED);

  /** States from which a ticket can be (re)assigned: the work has not started yet. */
  private static final Set<TicketState> ASSIGNABLE =
      EnumSet.of(TicketState.OPEN, TicketState.ASSIGNED);

  public MaintenanceTicket {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(vehicleId, "vehicleId");
    Objects.requireNonNull(faultCode, "faultCode");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(openedAt, "openedAt");
    Objects.requireNonNull(slaDeadline, "slaDeadline");
    if (!slaDeadline.isAfter(openedAt)) {
      throw new IllegalArgumentException("The SLA deadline must be after the opening time");
    }
    if (description != null) {
      description = description.strip();
      if (description.isEmpty()) {
        throw new IllegalArgumentException("A description, when given, cannot be blank");
      }
    }
    if (state == TicketState.OPEN && technicianId != null) {
      throw new IllegalArgumentException("An Open ticket has no technician yet");
    }
    if (WITH_TECHNICIAN.contains(state) && technicianId == null) {
      throw new IllegalArgumentException(
          "A ticket in state %s needs a technician".formatted(state));
    }
    if (workDone != null) {
      workDone = workDone.strip();
      if (workDone.isEmpty()) {
        throw new IllegalArgumentException("The work done, when given, cannot be blank");
      }
    }
    // US11: the closing time and the work done are there exactly when the ticket is Closed.
    boolean closed = state == TicketState.CLOSED;
    if (closed != (closedAt != null) || closed != (workDone != null)) {
      throw new IllegalArgumentException(
          "Only a Closed ticket, and every Closed ticket, has a closing time and the work done");
    }
    if (closedAt != null && closedAt.isBefore(openedAt)) {
      throw new IllegalArgumentException("A ticket cannot be closed before it was opened");
    }
  }

  /** A ticket that is not closed: no closing time and no record of the work. */
  public MaintenanceTicket(
      UUID id,
      UUID vehicleId,
      FaultCode faultCode,
      String description,
      TicketState state,
      Instant openedAt,
      Instant slaDeadline,
      UUID openedBy,
      UUID technicianId) {
    this(
        id,
        vehicleId,
        faultCode,
        description,
        state,
        openedAt,
        slaDeadline,
        openedBy,
        technicianId,
        null,
        null);
  }

  /** BR18, BR19: a ticket opened from a fault reading, due openedAt + sla. */
  public static MaintenanceTicket openFromFault(
      UUID id, UUID vehicleId, FaultCode faultCode, Instant openedAt, Duration sla) {
    return new MaintenanceTicket(
        id,
        vehicleId,
        faultCode,
        null,
        TicketState.OPEN,
        openedAt,
        deadline(openedAt, sla),
        null,
        null);
  }

  /**
   * BR19: a ticket opened by an operator for a fault the simulator cannot detect. The operator must
   * say what is wrong, so the description is required.
   */
  public static MaintenanceTicket openManually(
      UUID id,
      UUID vehicleId,
      FaultCode faultCode,
      String description,
      UUID operatorId,
      Instant openedAt,
      Duration sla) {
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(operatorId, "operatorId");
    return new MaintenanceTicket(
        id,
        vehicleId,
        faultCode,
        description,
        TicketState.OPEN,
        openedAt,
        deadline(openedAt, sla),
        operatorId,
        null);
  }

  public Optional<UUID> assignedTechnician() {
    return Optional.ofNullable(technicianId);
  }

  public Optional<UUID> openedByOperator() {
    return Optional.ofNullable(openedBy);
  }

  /** Whether the ticket was opened from a fault reading rather than by an operator. */
  public boolean isAutomatic() {
    return openedBy == null;
  }

  /**
   * BR20: only an active OPERATOR who is a field technician can be assigned. An assigned ticket can
   * be handed to another technician until the work starts.
   *
   * @throws NotAFieldTechnicianException if the account cannot take tickets
   * @throws ForbiddenTicketTransitionException if the work has started or the ticket is finished
   */
  public MaintenanceTicket assignTo(User technician) {
    Objects.requireNonNull(technician, "technician");
    if (!ASSIGNABLE.contains(state)) {
      throw new ForbiddenTicketTransitionException(state, "assigned");
    }
    if (!canBeAssignedTo(technician)) {
      throw new NotAFieldTechnicianException(technician.id());
    }
    return new MaintenanceTicket(
        id,
        vehicleId,
        faultCode,
        description,
        TicketState.ASSIGNED,
        openedAt,
        slaDeadline,
        openedBy,
        technician.id());
  }

  /**
   * US11: the assigned technician starts the work on an Assigned ticket.
   *
   * @throws ForbiddenTicketTransitionException if the ticket is not Assigned
   * @throws NotTheAssignedTechnicianException if someone else asks
   */
  public MaintenanceTicket start(UUID technicianId) {
    requireState(TicketState.ASSIGNED, "started");
    requireAssignedTo(technicianId);
    return new MaintenanceTicket(
        id,
        vehicleId,
        faultCode,
        description,
        TicketState.IN_PROGRESS,
        openedAt,
        slaDeadline,
        openedBy,
        this.technicianId);
  }

  /**
   * BR20: the assigned technician closes an InProgress ticket, recording the work done.
   *
   * @throws ForbiddenTicketTransitionException if the ticket is not InProgress
   * @throws NotTheAssignedTechnicianException if someone else asks
   * @throws IllegalArgumentException if workDone is blank or the closing is before the opening
   */
  public MaintenanceTicket close(UUID technicianId, String workDone, Instant at) {
    Objects.requireNonNull(workDone, "workDone");
    Objects.requireNonNull(at, "at");
    requireState(TicketState.IN_PROGRESS, "closed");
    requireAssignedTo(technicianId);
    if (workDone.isBlank()) {
      throw new IllegalArgumentException("Closing a ticket needs a record of the work done");
    }
    // The constructor refuses a closing before the opening.
    return new MaintenanceTicket(
        id,
        vehicleId,
        faultCode,
        description,
        TicketState.CLOSED,
        openedAt,
        slaDeadline,
        openedBy,
        this.technicianId,
        at,
        workDone);
  }

  /**
   * BR19: whether the SLA deadline was missed. An open ticket is late once now is after the
   * deadline; a closed one, if it was closed after the deadline. Exactly at the deadline is on
   * time.
   */
  public boolean isBreachedAt(Instant now) {
    Objects.requireNonNull(now, "now");
    return switch (state) {
      case CLOSED -> closedAt.isAfter(slaDeadline);
      case CANCELLED -> false;
      case OPEN, ASSIGNED, IN_PROGRESS -> now.isAfter(slaDeadline);
    };
  }

  /** BR19: from opening to closing, for the KPIs; empty until the ticket is closed. */
  public Optional<Duration> timeToRepair() {
    return Optional.ofNullable(closedAt).map(closed -> Duration.between(openedAt, closed));
  }

  private void requireState(TicketState expected, String action) {
    if (state != expected) {
      throw new ForbiddenTicketTransitionException(state, action);
    }
  }

  private void requireAssignedTo(UUID technician) {
    if (!Objects.equals(technicianId, technician)) {
      throw new NotTheAssignedTechnicianException(id, technician);
    }
  }

  /** BR20: whether the account can take tickets: an active OPERATOR who is a field technician. */
  public static boolean canBeAssignedTo(User user) {
    return user.role() == Role.OPERATOR && user.fieldTechnician() && user.active();
  }

  /** BR19: the deadline is the opening time plus the contracted repair time. */
  private static Instant deadline(Instant openedAt, Duration sla) {
    Objects.requireNonNull(openedAt, "openedAt");
    Objects.requireNonNull(sla, "sla");
    if (sla.isNegative() || sla.isZero()) {
      throw new IllegalArgumentException("The SLA must be a positive duration");
    }
    return openedAt.plus(sla);
  }
}
