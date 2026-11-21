package pt.ua.deti.tqs.p11.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;

/**
 * US13 (KAN-78), BR23: every (endpoint, caller) pair of
 * src/test/resources/security/access-matrix.csv is checked over HTTP, so a permission changed in
 * the code but not in the matrix (or the other way round) fails CI. Y = not 401/403; N = 401
 * without a token, 403 with one; OWN = allowed on the caller's own resource and, where the
 * ownership check exists, 403 on someone else's.
 *
 * <p>Bodies are empty JSON objects, so allowed writes stop at validation (400) or at a missing
 * endpoint (404) and change nothing. Endpoints of later stories already get their URL rule.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessMatrixApiIT {

  private static final String MATRIX = "/security/access-matrix.csv";
  private static final String ANONYMOUS = "ANONYMOUS";
  private static final String PASSWORD = "Passw0rd!";

  /**
   * OWN paths whose ownership check is implemented, so "someone else's" can be tested (403). Add a
   * path here when its story implements the check; until then only its URL rule is tested.
   */
  private static final Set<String> OWNERSHIP_CHECKED = Set.of("/users/{id}");

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  private RestClient http;
  private final Map<Role, Registered> callers = new EnumMap<>(Role.class);
  private Registered someoneElse;

  private record Row(String method, String path, Map<String, String> decisions) {}

  /** One account per role, plus another tourist whose data nobody else may read. */
  @BeforeAll
  void createOneAccountPerRole() {
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
    for (Role role : Role.values()) {
      String name = role.name().toLowerCase().replace('_', '.');
      Registered account =
          ApiClient.registerGuest(http, "Test " + role, name + "@example.com", PASSWORD);
      // Roles are set in the database; it is read on every request, so the token stays valid.
      jdbc.update("UPDATE users SET role = ? WHERE id = ?", role.name(), account.id());
      callers.put(role, account);
    }
    someoneElse = ApiClient.registerGuest(http, "Other Tourist", "other@example.com", PASSWORD);
  }

  static List<Row> matrix() {
    try (InputStream in = AccessMatrixApiIT.class.getResourceAsStream(MATRIX)) {
      Objects.requireNonNull(in, MATRIX + " not found");
      List<String> lines =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
              .lines()
              .map(String::strip)
              .filter(line -> !line.isEmpty() && !line.startsWith("#"))
              .toList();
      String[] header = lines.get(0).split(",");
      List<Row> rows = new ArrayList<>();
      for (String line : lines.subList(1, lines.size())) {
        String[] cells = line.split(",");
        Map<String, String> decisions = new LinkedHashMap<>();
        for (int i = 2; i < header.length; i++) {
          decisions.put(header[i], cells[i]);
        }
        rows.add(new Row(cells[0], cells[1], decisions));
      }
      return rows;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + MATRIX, e);
    }
  }

  static Stream<Arguments> everyPair() {
    return matrix().stream()
        .flatMap(row ->
            row.decisions().entrySet().stream()
                .map(cell ->
                    Arguments.of(row.method(), row.path(), cell.getKey(), cell.getValue())));
  }

  private int call(String method, String path, String caller) {
    HttpMethod httpMethod = HttpMethod.valueOf(method);
    RestClient.RequestBodySpec request =
        http.method(httpMethod)
            .uri(path)
            .headers(headers -> {
              if (!ANONYMOUS.equals(caller)) {
                headers.setBearerAuth(callers.get(Role.valueOf(caller)).token());
              }
            });
    if (httpMethod != HttpMethod.GET) {
      request.contentType(MediaType.APPLICATION_JSON).body(Map.of());
    }
    return request.retrieve().toBodilessEntity().getStatusCode().value();
  }

  private static String withId(String path, UUID id) {
    return path.replace("{id}", id.toString()).replace("{type}", "E_BIKE");
  }

  @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
  @MethodSource("everyPair")
  void everyCallerGetsTheDecisionOfTheMatrix(
      String method, String path, String caller, String decision) {
    boolean anonymous = ANONYMOUS.equals(caller);
    switch (decision) {
      // Y and N use another account's id: Y must hold for any resource, N for none.
      case "Y" ->
          assertThat(call(method, withId(path, someoneElse.id()), caller))
              .as("%s %s as %s should be allowed", method, path, caller)
              .isNotIn(401, 403);
      case "N" ->
          assertThat(call(method, withId(path, someoneElse.id()), caller))
              .as("%s %s as %s should be denied", method, path, caller)
              .isEqualTo(anonymous ? 401 : 403);
      case "OWN" -> {
        UUID own = callers.get(Role.valueOf(caller)).id();
        assertThat(call(method, withId(path, own), caller))
            .as("%s %s as %s on their own resource should be allowed", method, path, caller)
            .isNotIn(401, 403);
        if (OWNERSHIP_CHECKED.contains(path)) {
          assertThat(call(method, withId(path, someoneElse.id()), caller))
              .as("%s %s as %s on someone else's resource should be denied", method, path, caller)
              .isEqualTo(403);
        }
      }
      default -> throw new IllegalStateException("Unknown decision in the matrix: " + decision);
    }
  }

  /** A new endpoint without a row in the matrix fails CI, so its permissions are decided. */
  @Test
  void everyEndpointOfTheApi_isInTheAccessMatrix() {
    Set<String> inMatrix =
        matrix().stream().map(row -> row.method() + " " + row.path()).collect(Collectors.toSet());

    Set<String> endpoints =
        mappings.getHandlerMethods().entrySet().stream()
            .filter(e -> e.getValue().getBeanType().getPackageName().startsWith("pt.ua.deti.tqs"))
            .flatMap(e ->
                e.getKey().getMethodsCondition().getMethods().stream()
                    .flatMap(verb ->
                        e.getKey().getPatternValues().stream()
                            .map(pattern -> verb.name() + " " + pattern)))
            .collect(Collectors.toSet());

    assertThat(endpoints).isNotEmpty();
    assertThat(inMatrix).containsAll(endpoints);
  }
}
