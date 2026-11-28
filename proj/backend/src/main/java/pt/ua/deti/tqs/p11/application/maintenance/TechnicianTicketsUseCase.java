package pt.ua.deti.tqs.p11.application.maintenance;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleNotFoundException;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;

/**
 * What a field technician does with their tickets (US11): see them, most urgent first, with where
 * the vehicle is; start the work; and close the ticket with a record of the work done. Starting and
 * closing lock the ticket's vehicle first, as telemetry and manual tickets do (K3), so changes to
 * the tickets of one vehicle happen one after the other. BR20: when the last open ticket of a
 * vehicle in Maintenance is closed, the vehicle returns to Available.
 */
public class TechnicianTicketsUseCase {

  private final TicketRepository tickets;
  private final VehicleRepository vehicles;
  private final HubRepository hubs;
  private final VehicleStateChangeRepository history;
  private final TransactionRunner transactions;
  private final Clock clock;

  public TechnicianTicketsUseCase(
      TicketRepository tickets,
      VehicleRepository vehicles,
      HubRepository hubs,
      VehicleStateChangeRepository history,
      TransactionRunner transactions,
      Clock clock) {
    this.tickets = Objects.requireNonNull(tickets, "tickets");
    this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    this.hubs = Objects.requireNonNull(hubs, "hubs");
    this.history = Objects.requireNonNull(history, "history");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * BR19: the technician's open tickets, nearest deadline first, with where to find each vehicle.
   */
  public List<TechnicianTicket> myTickets(UUID technicianId) {
    Instant now = clock.instant();
    return tickets.findOpenAssignedTo(technicianId).stream()
        .map(ticket -> entry(ticket, now))
        .toList();
  }

  /**
   * The technician starts the work on an Assigned ticket.
   *
   * @throws TicketNotFoundException if the ticket is unknown
   */
  public MaintenanceTicket start(UUID ticketId, UUID technicianId) {
    return transactions.inTransaction(() -> {
      MaintenanceTicket started = lock(ticketId).ticket().start(technicianId);
      tickets.save(started);
      return started;
    });
  }

  /**
   * BR20: the technician closes an InProgress ticket; the vehicle returns to service if it was in
   * Maintenance and no other ticket is open for it.
   *
   * @throws TicketNotFoundException if the ticket is unknown
   */
  public MaintenanceTicket close(CloseTicketCommand command) {
    return transactions.inTransaction(() -> {
      Locked locked = lock(command.ticketId());
      Instant now = clock.instant();
      MaintenanceTicket closed =
          locked.ticket().close(command.technicianId(), command.workDone(), now);
      tickets.save(closed);
      Vehicle vehicle = locked.vehicle();
      // Saved and flushed above, so the closed ticket no longer counts as open.
      if (vehicle.state() == VehicleState.MAINTENANCE && !tickets.existsOpenFor(vehicle.id())) {
        Vehicle back = vehicle.returnToService();
        vehicles.save(back);
        history.save(VehicleStateChange.between(vehicle, back, now));
      }
      return closed;
    });
  }

  /** A ticket and its vehicle, read with the vehicle locked. */
  private record Locked(MaintenanceTicket ticket, Vehicle vehicle) {}

  /**
   * Locks the ticket's vehicle, then reads the ticket again: a change to another ticket of the same
   * vehicle (a reading, a manual ticket, a closing) may have committed while waiting for the lock.
   */
  private Locked lock(UUID ticketId) {
    UUID vehicleId = ticket(ticketId).vehicleId();
    Vehicle vehicle =
        vehicles
            .findByIdForUpdate(vehicleId)
            .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
    return new Locked(ticket(ticketId), vehicle);
  }

  private MaintenanceTicket ticket(UUID ticketId) {
    return tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
  }

  private TechnicianTicket entry(MaintenanceTicket ticket, Instant now) {
    Vehicle vehicle =
        vehicles
            .findById(ticket.vehicleId())
            .orElseThrow(() -> new VehicleNotFoundException(ticket.vehicleId()));
    Optional<Hub> hub = vehicle.currentHub().flatMap(hubs::findById);
    return new TechnicianTicket(
        ticket,
        vehicle.code().value(),
        hub.map(found -> found.code().value()).orElse(null),
        hub.map(Hub::name).orElse(null),
        vehicle.lastPosition().orElse(null),
        ticket.isBreachedAt(now));
  }
}
