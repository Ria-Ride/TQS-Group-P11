package pt.ua.deti.tqs.p11.application.maintenance;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
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
    throw new UnsupportedOperationException("US11");
  }

  /**
   * The technician starts the work on an Assigned ticket.
   *
   * @throws TicketNotFoundException if the ticket is unknown
   */
  public MaintenanceTicket start(UUID ticketId, UUID technicianId) {
    throw new UnsupportedOperationException("US11");
  }

  /**
   * BR20: the technician closes an InProgress ticket; the vehicle returns to service if it was in
   * Maintenance and no other ticket is open for it.
   *
   * @throws TicketNotFoundException if the ticket is unknown
   */
  public MaintenanceTicket close(CloseTicketCommand command) {
    throw new UnsupportedOperationException("US11");
  }
}
