package pt.ua.deti.tqs.p11.support;

import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** HTTP client for tests: talks to the running app and returns 4xx/5xx instead of throwing. */
public final class ApiClient {

  public static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {};

  /** A guest account created through the API, with the token it was given. */
  public record Registered(UUID id, String token) {}

  private ApiClient() {}

  public static RestClient forPort(int port) {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }

  /** Registers a guest through POST /auth/guest; for tests that need a real account as setup. */
  public static Registered registerGuest(
      RestClient http, String name, String email, String password) {
    Map<String, Object> body =
        http.post()
            .uri("/auth/guest")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", name, "email", email, "password", password))
            .retrieve()
            .toEntity(JSON_OBJECT)
            .getBody();
    return new Registered(
        UUID.fromString((String) body.get("userId")), (String) body.get("accessToken"));
  }
}
