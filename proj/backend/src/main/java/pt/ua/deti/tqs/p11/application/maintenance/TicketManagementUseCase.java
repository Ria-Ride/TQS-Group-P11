package pt.ua.deti.tqs.p11.application.maintenance;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * What an operator does with maintenance tickets (US10): open one by hand, list the open ones,
 * assign a field technician and list who can be assigned. Opening locks the vehicle first, as a
 * telemetry reading does, so a manual ticket and a reading of the same fault cannot both open one.
 */
public class TicketManagementUseCase {

  private final TicketRepository tickets;
  private final UserRepository users;
  private final VehicleRepository vehicles;
  private final OpenFaultTicketUseCase opener;
  private final TransactionRunner transactions;

  public TicketManagementUseCase(
      TicketRepository tickets,
      UserRepository users,
      VehicleRepository vehicles,
      OpenFaultTicketUseCase opener,
      TransactionRunner transactions) {
    this.tickets = Objects.requireNonNull(tickets, "tickets");
    this.users = Objects.requireNonNull(users, "users");
    this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    this.opener = Objects.requireNonNull(opener, "opener");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
  }

  /**
   * BR18, BR19, BR20: a manual ticket for the vehicle.
   *
   * @throws pt.ua.deti.tqs.p11.application.fleet.VehicleNotFoundException if the vehicle is unknown
   */
  public MaintenanceTicket open(OpenTicketCommand command) {
    throw new UnsupportedOperationException("US10");
  }

  /** Every open ticket, the nearest SLA deadline first. */
  public List<MaintenanceTicket> openTickets() {
    throw new UnsupportedOperationException("US10");
  }

  /**
   * BR20: assigns the ticket to a field technician.
   *
   * @throws TicketNotFoundException if the ticket is unknown
   * @throws pt.ua.deti.tqs.p11.domain.maintenance.NotAFieldTechnicianException if the account is
   *     unknown or cannot take tickets
   */
  public MaintenanceTicket assign(UUID ticketId, UUID technicianId) {
    throw new UnsupportedOperationException("US10");
  }

  /** BR20: the accounts a ticket can be assigned to, by name. */
  public List<User> technicians() {
    throw new UnsupportedOperationException("US10");
  }
}
