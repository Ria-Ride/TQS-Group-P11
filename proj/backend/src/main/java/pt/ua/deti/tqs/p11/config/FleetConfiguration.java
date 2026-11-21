package pt.ua.deti.tqs.p11.config;

import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pt.ua.deti.tqs.p11.adapter.out.persistence.SpringTransactionRunner;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.HubManagementUseCase;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.OpenTickets;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;

/** Wires the fleet use cases (US08) to their adapters (ports and adapters). */
@Configuration
class FleetConfiguration {

  @Bean
  TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
    return new SpringTransactionRunner(new TransactionTemplate(transactionManager));
  }

  /**
   * Maintenance tickets arrive with US10, which replaces this bean with one that reads them. Until
   * then no vehicle has an open ticket.
   */
  @Bean
  OpenTickets openTickets() {
    return vehicleId -> false;
  }

  @Bean
  HubManagementUseCase hubManagementUseCase(
      HubRepository hubs, VehicleRepository vehicles, TransactionRunner transactions) {
    return new HubManagementUseCase(hubs, vehicles, transactions, UUID::randomUUID);
  }

  @Bean
  VehicleLifecycleUseCase vehicleLifecycleUseCase(
      VehicleRepository vehicles,
      HubRepository hubs,
      VehicleStateChangeRepository history,
      OpenTickets openTickets,
      TransactionRunner transactions,
      Clock clock) {
    return new VehicleLifecycleUseCase(
        vehicles, hubs, history, openTickets, transactions, clock, UUID::randomUUID);
  }
}
