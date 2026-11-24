package pt.ua.deti.tqs.p11.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.MosquittoBroker;

/**
 * Starts the whole application on a random port with a real PostgreSQL and a real MQTT broker for
 * the scenarios. The telemetry adapter is on (US09); the broker is shared by all scenarios.
 */
@CucumberContextConfiguration
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.telemetry.enabled=true")
@Import(TestcontainersConfiguration.class)
public class CucumberSpringConfiguration {

  static final MosquittoBroker BROKER = new MosquittoBroker();

  @DynamicPropertySource
  static void brokerAddress(DynamicPropertyRegistry registry) {
    BROKER.start();
    registry.add("app.telemetry.host", BROKER::host);
    registry.add("app.telemetry.port", BROKER::port);
  }
}
