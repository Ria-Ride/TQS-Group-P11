package pt.ua.deti.tqs.p11.config;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.OpenTickets;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.application.maintenance.FaultBookingCancellation;
import pt.ua.deti.tqs.p11.application.maintenance.OpenFaultTicketUseCase;
import pt.ua.deti.tqs.p11.application.maintenance.TicketManagementUseCase;
import pt.ua.deti.tqs.p11.application.maintenance.TicketRepository;
import pt.ua.deti.tqs.p11.application.user.UserRepository;

/** Wires the maintenance use cases (US10) to their adapters (ports and adapters). */
@Configuration
@EnableConfigurationProperties(MaintenanceProperties.class)
class MaintenanceConfiguration {

  /** BR16: fleet asks whether a vehicle may be activated; maintenance answers from its tickets. */
  @Bean
  OpenTickets openTickets(TicketRepository tickets) {
    return tickets::existsOpenFor;
  }

  @Bean
  OpenFaultTicketUseCase openFaultTicketUseCase(
      TicketRepository tickets,
      VehicleRepository vehicles,
      VehicleStateChangeRepository history,
      FaultBookingCancellation bookings,
      MaintenanceProperties properties,
      Clock clock) {
    return new OpenFaultTicketUseCase(
        tickets, vehicles, history, bookings, properties.sla(), clock, UUID::randomUUID);
  }

  @Bean
  TicketManagementUseCase ticketManagementUseCase(
      TicketRepository tickets,
      UserRepository users,
      VehicleRepository vehicles,
      OpenFaultTicketUseCase opener,
      TransactionRunner transactions) {
    return new TicketManagementUseCase(tickets, users, vehicles, opener, transactions);
  }
}
