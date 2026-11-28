package pt.ua.deti.tqs.p11.config;

import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.billing.PaymentPort;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.application.booking.BookingRepository;
import pt.ua.deti.tqs.p11.application.booking.DepositReturn;
import pt.ua.deti.tqs.p11.application.booking.DepositSettlement;
import pt.ua.deti.tqs.p11.application.booking.EndUseUseCase;
import pt.ua.deti.tqs.p11.application.booking.FaultReservationCancellation;
import pt.ua.deti.tqs.p11.application.booking.ManageBookingUseCase;
import pt.ua.deti.tqs.p11.application.booking.PassCoverages;
import pt.ua.deti.tqs.p11.application.booking.StartUseUseCase;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.maintenance.FaultBookingCancellation;
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

  /** BR13: how every booking that ends without a ride gives its deposit back. */
  @Bean
  DepositReturn depositReturn(PaymentRepository payments, PaymentPort gateway, Clock clock) {
    return new DepositReturn(payments, gateway, clock);
  }

  /** US05: reading, starting, cancelling and expiring bookings. */
  @Bean
  ManageBookingUseCase manageBookingUseCase(
      VehicleLifecycleUseCase fleet,
      BookingRepository bookings,
      DepositReturn deposits,
      VehicleCommandPort commands,
      TransactionRunner transactions,
      Clock clock) {
    return new ManageBookingUseCase(fleet, bookings, deposits, commands, transactions, clock);
  }

  /** BR13: how the deposit of an ended ride is captured and released. */
  @Bean
  DepositSettlement depositSettlement(
      PaymentRepository payments, PaymentPort gateway, Clock clock) {
    return new DepositSettlement(payments, gateway, clock);
  }

  /** US06: ending a ride at a hub. */
  @Bean
  EndUseUseCase endUseUseCase(
      VehicleLifecycleUseCase fleet,
      BookingRepository bookings,
      HubRepository hubs,
      VehicleRepository vehicles,
      VehicleCommandPort commands,
      DepositSettlement deposits,
      TransactionRunner transactions,
      Clock clock) {
    return new EndUseUseCase(
        fleet, bookings, hubs, vehicles, commands, deposits, transactions, clock);
  }

  /** BR18: maintenance calls it when a Reserved vehicle reports a fault (US10). */
  @Bean
  FaultBookingCancellation faultBookingCancellation(
      BookingRepository bookings, DepositReturn deposits) {
    return new FaultReservationCancellation(bookings, deposits);
  }
}
