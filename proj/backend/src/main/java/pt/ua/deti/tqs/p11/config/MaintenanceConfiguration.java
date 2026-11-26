package pt.ua.deti.tqs.p11.config;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.application.maintenance.FaultBookingCancellation;
import pt.ua.deti.tqs.p11.application.maintenance.OpenFaultTicketUseCase;
import pt.ua.deti.tqs.p11.application.maintenance.TicketRepository;

/** Wires the maintenance use cases (US10) to their adapters (ports and adapters). */
@Configuration
@EnableConfigurationProperties(MaintenanceProperties.class)
class MaintenanceConfiguration {

  /**
   * Bookings arrive with US05, which replaces this bean with one that cancels the Reserved booking
   * and releases its deposit. Until then no vehicle can be Reserved, so there is nothing to cancel.
   */
  @Bean
  FaultBookingCancellation faultBookingCancellation() {
    return vehicleId -> {};
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
}
