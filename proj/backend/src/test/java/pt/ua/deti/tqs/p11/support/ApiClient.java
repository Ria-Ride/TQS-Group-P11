package pt.ua.deti.tqs.p11.support;

import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

/** HTTP client for tests: talks to the running app and returns 4xx/5xx instead of throwing. */
public final class ApiClient {

  public static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {};

  private ApiClient() {}

  public static RestClient forPort(int port) {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }
}