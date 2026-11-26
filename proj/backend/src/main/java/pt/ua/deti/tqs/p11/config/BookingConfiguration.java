package pt.ua.deti.tqs.p11.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.application.booking.UsageRulesPort;
import pt.ua.deti.tqs.p11.domain.booking.UsageRules;

/** Wires the booking use cases (US04 to US06) to their adapters (ports and adapters). */
@Configuration
@EnableConfigurationProperties(BookingProperties.class)
class BookingConfiguration {

  /**
   * The seeded rules of use, built once at start-up, so an invalid value stops the application
   * instead of failing a tourist's booking. US14 replaces this bean with one that reads the rules
   * the client edits.
   */
  @Bean
  UsageRulesPort usageRulesPort(BookingProperties properties) {
    UsageRules rules = properties.toRules();
    return () -> rules;
  }
}
