package pt.ua.deti.tqs.p11.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Simulated payment gateway (KAN-96). It holds no real money and no card data: the token in each
 * request is a test card that decides the answer, like the test cards of real payment sandboxes.
 * Charges are kept in memory and lost on restart.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentGatewayApplication.class, args);
  }
}
