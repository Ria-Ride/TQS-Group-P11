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
import pt.ua.deti.tqs.p11.application.fleet.VehicleSearchUseCase;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.application.rules.RulesOfUseUseCase;

/** Wires the fleet use cases (US08) to their adapters (ports and adapters). */
@Configuration
class FleetConfiguration {

  @Bean
  TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
    return new SpringTransactionRunner(new TransactionTemplate(transactionManager));
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

  @Bean
  VehicleSearchUseCase vehicleSearchUseCase(
      HubRepository hubs, VehicleRepository vehicles, RulesOfUseUseCase rules) {
    // The battery rule is read from the rules of use at every search, so a change by the client
    // manager (US14) reaches the next search.
    return new VehicleSearchUseCase(hubs, vehicles, () -> rules.current().batteryRule());
  }
}
