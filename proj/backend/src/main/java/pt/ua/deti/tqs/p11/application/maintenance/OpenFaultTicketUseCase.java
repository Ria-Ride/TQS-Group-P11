package pt.ua.deti.tqs.p11.application.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.application.telemetry.FaultReportListener;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;

/**
 * Opens a maintenance ticket when a reading reports a fault (US10). BR20: a fault that already has
 * an open ticket opens no other. BR18: an Available or Reserved vehicle goes to Maintenance (a
 * Reserved one also loses its booking), an InUse ride is not interrupted, and any other vehicle
 * keeps its state. BR19: the ticket is due its opening time plus the SLA. A Retired vehicle is out
 * of the fleet for good, so its faults are ignored.
 */
public class OpenFaultTicketUseCase implements FaultReportListener {

  private static final Logger LOG = LoggerFactory.getLogger(OpenFaultTicketUseCase.class);

  private final TicketRepository tickets;
  private final VehicleRepository vehicles;
  private final VehicleStateChangeRepository history;
  private final FaultBookingCancellation bookings;
  private final Duration sla;
  private final Clock clock;
  private final Supplier<UUID> idGenerator;

  public OpenFaultTicketUseCase(
      TicketRepository tickets,
      VehicleRepository vehicles,
      VehicleStateChangeRepository history,
      FaultBookingCancellation bookings,
      Duration sla,
      Clock clock,
      Supplier<UUID> idGenerator) {
    this.tickets = Objects.requireNonNull(tickets, "tickets");
    this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    this.history = Objects.requireNonNull(history, "history");
    this.bookings = Objects.requireNonNull(bookings, "bookings");
    this.sla = Objects.requireNonNull(sla, "sla");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
  }

  @Override
  public void faultReported(Vehicle vehicle, FaultCode fault) {
    if (vehicle.state() == VehicleState.RETIRED) {
      LOG.warn("Fault {} of retired vehicle {} ignored", fault.value(), vehicle.code().value());
      return;
    }
    if (tickets.findOpenFor(vehicle.id(), fault).isPresent()) {
      return; // BR20: the simulator repeats the fault while it lasts; it is already covered.
    }
    Instant now = clock.instant();
    tickets.save(MaintenanceTicket.openFromFault(idGenerator.get(), vehicle.id(), fault, now, sla));
    Vehicle after = vehicle.reportFault();
    if (after.state() != vehicle.state()) {
      vehicles.save(after);
      history.save(VehicleStateChange.between(vehicle, after, now));
    }
    if (vehicle.state() == VehicleState.RESERVED) {
      bookings.cancelReservedBookingOf(vehicle.id());
    }
  }
}
