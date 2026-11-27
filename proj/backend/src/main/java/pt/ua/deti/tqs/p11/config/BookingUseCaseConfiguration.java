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
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.application.rules.RulesOfUseUseCase;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.booking.BookingPolicy;

/** Wires the booking use cases (US04 to US06) to their ports (ports and adapters). */
@Configuration
class BookingUseCaseConfiguration {

  /**
   * A policy per request, with the version of the rules of use in force now (US14), battery rule
   * (BR1) included, so a change by the client manager applies to the next booking (BR21).
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
      RulesOfUseUseCase rules,
      TransactionRunner transactions,
      Clock clock) {
    return new StartUseUseCase(
        fleet,
        users,
        passes,
        bookings,
        payments,
        gateway,
        commands,
        () -> new BookingPolicy(rules.current()),
        transactions,
        clock,
        UUID::randomUUID);
  }
}
