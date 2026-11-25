package pt.ua.deti.tqs.p11.support;

import java.nio.file.Path;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * The simulated payment gateway (proj/payment-gateway) in a container, built from its Dockerfile,
 * for integration and acceptance tests. The test card in each charge decides the answer:
 * tok_approve, tok_decline, or tok_timeout (answers only after {@link #TIMEOUT_DELAY}, so the
 * backend's read timeout fires first). The image is kept after the tests, so it is built once.
 */
public final class PaymentGatewayContainer {

  /** How long the gateway holds a tok_timeout answer; keep the backend's read timeout below it. */
  public static final Duration TIMEOUT_DELAY = Duration.ofSeconds(5);

  private static final int HTTP_PORT = 8080;
  private static final Path SOURCES = Path.of("..", "payment-gateway");

  private final GenericContainer<?> container =
      new GenericContainer<>(
              new ImageFromDockerfile("p11-payment-gateway-test", false)
                  .withFileFromPath("Dockerfile", SOURCES.resolve("Dockerfile"))
                  .withFileFromPath("pom.xml", SOURCES.resolve("pom.xml"))
                  .withFileFromPath("mvnw", SOURCES.resolve("mvnw"))
                  .withFileFromPath(".mvn", SOURCES.resolve(".mvn"))
                  .withFileFromPath("src", SOURCES.resolve("src")))
          .withEnv("GATEWAY_TIMEOUT_DELAY", TIMEOUT_DELAY.toString())
          .withExposedPorts(HTTP_PORT)
          .waitingFor(Wait.forHttp("/health").forStatusCode(200));

  public void start() {
    container.start();
  }

  public void stop() {
    container.stop();
  }

  public String baseUrl() {
    return "http://%s:%d".formatted(container.getHost(), container.getMappedPort(HTTP_PORT));
  }
}
