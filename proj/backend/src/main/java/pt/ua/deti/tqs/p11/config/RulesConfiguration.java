package pt.ua.deti.tqs.p11.config;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.pass.PartnerAccommodationRepository;
import pt.ua.deti.tqs.p11.application.pass.PassTypeManagementUseCase;
import pt.ua.deti.tqs.p11.application.pass.PassTypeRepository;
import pt.ua.deti.tqs.p11.application.pass.RandomStayCodeGenerator;
import pt.ua.deti.tqs.p11.application.pass.StayCodeGenerator;
import pt.ua.deti.tqs.p11.application.pass.StayManagementUseCase;
import pt.ua.deti.tqs.p11.application.pass.StayRepository;
import pt.ua.deti.tqs.p11.application.rules.RulesOfUseRepository;
import pt.ua.deti.tqs.p11.application.rules.RulesOfUseUseCase;

/**
 * Wires what the client manager edits (US14): the versioned rules of use, pass types, partner
 * hotels and stay codes, to their adapters (ports and adapters).
 */
@Configuration
class RulesConfiguration {

  @Bean
  RulesOfUseUseCase rulesOfUseUseCase(
      RulesOfUseRepository rules, TransactionRunner transactions, Clock clock) {
    return new RulesOfUseUseCase(rules, transactions, clock);
  }

  @Bean
  PassTypeManagementUseCase passTypeManagementUseCase(
      PassTypeRepository passTypes, TransactionRunner transactions) {
    Supplier<UUID> ids = UUID::randomUUID;
    return new PassTypeManagementUseCase(passTypes, transactions, ids);
  }

  /** Codes are read out at a hotel desk, so they come from a source that cannot be guessed. */
  @Bean
  StayCodeGenerator stayCodeGenerator() {
    return new RandomStayCodeGenerator(new java.security.SecureRandom());
  }

  @Bean
  StayManagementUseCase stayManagementUseCase(
      PartnerAccommodationRepository accommodations,
      StayRepository stays,
      StayCodeGenerator codes) {
    return new StayManagementUseCase(accommodations, stays, codes, UUID::randomUUID);
  }
}
