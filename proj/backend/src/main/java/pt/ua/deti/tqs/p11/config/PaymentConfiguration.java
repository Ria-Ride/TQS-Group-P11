package pt.ua.deti.tqs.p11.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.adapter.out.payment.PaymentGatewayAdapter;
import pt.ua.deti.tqs.p11.application.billing.PaymentPort;

/** Wires the payment port to the simulated gateway's HTTP adapter (ports and adapters, K2). */
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
class PaymentConfiguration {

  @Bean
  PaymentPort paymentPort(PaymentProperties properties) {
    return new PaymentGatewayAdapter(
        properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
  }
}
