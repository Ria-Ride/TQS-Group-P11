package pt.ua.deti.tqs.p11.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Calls the fleet endpoints (US08) as one user, for API and acceptance tests. Returns 4xx/5xx
 * responses instead of throwing, like ApiClient.
 */
public final class FleetApi {

  public static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
      new ParameterizedTypeReference<>() {};

  private final RestClient http;
  private final String token;

  public FleetApi(RestClient http, String token) {
    this.http = http;
    this.token = token;
  }

  public ResponseEntity<Map<String, Object>> registerHub(
      String code, String name, double latitude, double longitude, int capacity) {
    return send(
        HttpMethod.POST,
        "/hubs",
        Map.of(
            "code", code,
            "name", name,
            "latitude", latitude,
            "longitude", longitude,
            "capacity", capacity));
  }

  /** A hub at a fixed position, for tests where only the code and capacity matter. */
  public UUID hub(String code, int capacity) {
    return id(created(registerHub(code, "Hub " + code, 40.6405, -8.6538, capacity)));
  }

  public ResponseEntity<Map<String, Object>> changeCapacity(UUID hubId, int capacity) {
    return send(HttpMethod.PATCH, "/hubs/" + hubId, Map.of("capacity", capacity));
  }

  public ResponseEntity<Map<String, Object>> registerVehicle(String code, String type, UUID hubId) {
    return send(HttpMethod.POST, "/vehicles", Map.of("code", code, "type", type, "hubId", hubId));
  }

  /** A registered (Deactivated) e-bike at the hub. */
  public UUID bike(String code, UUID hubId) {
    return id(created(registerVehicle(code, "E_BIKE", hubId)));
  }

  /** hubId may be null: it is left out of the body. */
  public ResponseEntity<Map<String, Object>> transition(UUID vehicleId, String action, UUID hubId) {
    Map<String, Object> body = new HashMap<>();
    body.put("action", action);
    if (hubId != null) {
      body.put("hubId", hubId);
    }
    return send(HttpMethod.POST, "/vehicles/" + vehicleId + "/transitions", body);
  }

  public ResponseEntity<Map<String, Object>> transition(UUID vehicleId, String action) {
    return transition(vehicleId, action, null);
  }

  public ResponseEntity<Map<String, Object>> get(String uri) {
    return http.get()
        .uri(uri)
        .headers(headers -> headers.setBearerAuth(token))
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  public ResponseEntity<List<Map<String, Object>>> getList(String uri) {
    return http.get()
        .uri(uri)
        .headers(headers -> headers.setBearerAuth(token))
        .retrieve()
        .toEntity(JSON_LIST);
  }

  public ResponseEntity<Map<String, Object>> send(HttpMethod method, String uri, Object body) {
    return http.method(method)
        .uri(uri)
        .headers(headers -> headers.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  /** For setup steps: fails with the response, so a broken setup is not mistaken for the test. */
  public static ResponseEntity<Map<String, Object>> created(
      ResponseEntity<Map<String, Object>> response) {
    if (response.getStatusCode().value() != 201) {
      throw new IllegalStateException(
          "Setup expected 201 but got " + response.getStatusCode() + ": " + response.getBody());
    }
    return response;
  }

  public static UUID id(ResponseEntity<Map<String, Object>> response) {
    return UUID.fromString((String) response.getBody().get("id"));
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> errors(ResponseEntity<Map<String, Object>> response) {
    return (Map<String, Object>) response.getBody().get("errors");
  }
}
