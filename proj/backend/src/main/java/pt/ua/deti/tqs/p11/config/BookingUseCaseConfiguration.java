package pt.ua.deti.tqs.p11.config;

import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.billing.PaymentPort;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.application.booking.BookingRepository;
import pt.ua.deti.tqs.p11.application.booking.PassCoverages;
import pt.ua.deti.tqs.p11.application.booking.StartUseUseCase;
import pt.ua.deti.tqs.p11.application.booking.UsageRulesPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.booking.BookingPolicy;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryRule;

/**
 * Wires the booking use cases (US04 to US06) to their ports. Apart from BookingConfiguration, which
 * only reads the rules of use, so that one can be checked alone at start-up.
 */
@Configuration
class BookingUseCaseConfiguration {

  /**
   * A policy per request, with the rules of use in force now and the fleet's battery rule (BR1), so
   * a change of the rules applies to the next booking (BR21).
   */
  @Bean
  StartUseUseCase startUseUseCase(
      VehicleLifecycleUseCase fleet,
      UserRepository users,
      PassCoverages passes,
      BookingRepository bookings,
      PaymentRepository payments,
      PaymentPort gateway,
      VehicleCommandPort commands,
      UsageRulesPort usageRules,
      FleetProperties fleetProperties,
      TransactionRunner transactions,
      Clock clock) {
    BatteryRule battery = fleetProperties.toRule();
    return new StartUseUseCase(
        fleet,
        users,
        passes,
        bookings,
        payments,
        gateway,
        commands,
        () -> new BookingPolicy(usageRules.current(), battery),
        transactions,
        clock,
        UUID::randomUUID);
  }
}
