package pt.ua.deti.tqs.p11.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.MosquittoBroker;
import pt.ua.deti.tqs.p11.support.PaymentGatewayContainer;

/**
 * Starts the whole application on a random port with a real PostgreSQL, a real MQTT broker and the
 * simulated payment gateway for the scenarios. The telemetry adapter is on (US09); the broker and
 * the gateway are shared by all scenarios. The payment read timeout is short, so the timed-out
 * payment scenario (US03) takes one second.
 */
@CucumberContextConfiguration
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.telemetry.enabled=true")
@Import(TestcontainersConfiguration.class)
public class CucumberSpringConfiguration {

  static final MosquittoBroker BROKER = new MosquittoBroker();
  static final PaymentGatewayContainer GATEWAY = new PaymentGatewayContainer();

  @DynamicPropertySource
  static void brokerAddress(DynamicPropertyRegistry registry) {
    BROKER.start();
    registry.add("app.telemetry.host", BROKER::host);
    registry.add("app.telemetry.port", BROKER::port);
  }

  @DynamicPropertySource
  static void gatewayAddress(DynamicPropertyRegistry registry) {
    GATEWAY.start();
    registry.add("app.payment.base-url", GATEWAY::baseUrl);
    registry.add("app.payment.read-timeout", () -> "PT1S");
  }
}
