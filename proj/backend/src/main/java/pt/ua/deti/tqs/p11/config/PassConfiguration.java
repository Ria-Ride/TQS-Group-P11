package pt.ua.deti.tqs.p11.config;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.billing.PaymentPort;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.application.pass.PassPurchaseUseCase;
import pt.ua.deti.tqs.p11.application.pass.PassRepository;
import pt.ua.deti.tqs.p11.application.pass.PassTypeRepository;
import pt.ua.deti.tqs.p11.application.pass.StayRepository;

/** Wires the pass use case (US03) to its adapters (ports and adapters). */
@Configuration
@EnableConfigurationProperties(PassProperties.class)
class PassConfiguration {

  @Bean
  PassPurchaseUseCase passPurchaseUseCase(
      PassTypeRepository passTypes,
      StayRepository stays,
      PassRepository passes,
      PaymentRepository payments,
      PaymentPort gateway,
      TransactionRunner transactions,
      Clock clock,
      PassProperties properties) {
    return new PassPurchaseUseCase(
        passTypes,
        stays,
        passes,
        payments,
        gateway,
        transactions,
        clock,
        properties.zone(),
        UUID::randomUUID);
  }
}
