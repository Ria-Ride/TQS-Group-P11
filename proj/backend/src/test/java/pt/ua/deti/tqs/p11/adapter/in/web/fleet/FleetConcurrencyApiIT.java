package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * K3: concurrent operations on the same hub slot or the same vehicle. All requests are released at
 * once; without the row locks several of them would read the same state and all succeed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FleetConcurrencyApiIT {

  /** Below the default connection pool (10), so no request waits for a connection. */
  private static final int REQUESTS = 8;

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi operator;

  @BeforeEach
  void loginAsOperatorWithAnEmptyFleet() {
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
    jdbc.update("DELETE FROM users");
    RestClient http = ApiClient.forPort(port);
    Registered rui =
        ApiClient.registerGuest(http, "Rui Costa", "rui.costa@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", rui.id());
    operator = new FleetApi(http, rui.token());
  }

  /** Sends REQUESTS requests at the same moment and returns their status codes. */
  private List<Integer> concurrently(IntFunction<Integer> request) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < REQUESTS; i++) {
        int n = i;
        Callable<Integer> task =
            () -> {
              start.await();
              return request.apply(n);
            };
        results.add(pool.submit(task));
      }
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> result : results) {
        statuses.add(result.get());
      }
      return statuses;
    }
  }

  /** BR14: the last free slot of a hub goes to exactly one of the vehicles registered at once. */
  @Test
  void register_manyVehiclesForTheLastFreeSlot_exactlyOneIsCreated() throws Exception {
    UUID hub = operator.hub("hub-03", 10);
    for (int i = 1; i <= 9; i++) {
      operator.bike("scooter-%04d".formatted(i), hub);
    }

    List<Integer> statuses =
        concurrently(n ->
            operator
                .registerVehicle("bike-%04d".formatted(n), "E_BIKE", hub)
                .getStatusCode()
                .value());

    assertThat(statuses).containsOnlyOnce(201).filteredOn(s -> s != 201).containsOnly(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM vehicles WHERE hub_id = ?", Integer.class, hub))
        .isEqualTo(10);
  }

  /**
   * BR16: one vehicle moved by several requests at once changes state once and is recorded once.
   */
  @Test
  void startMove_sameVehicleManyTimesAtOnce_exactlyOneSucceeds() throws Exception {
    UUID hub = operator.hub("hub-03", 10);
    UUID bike = operator.bike("bike-0009", hub);
    operator.transition(bike, "ACTIVATE");

    List<Integer> statuses =
        concurrently(n -> operator.transition(bike, "START_MOVE").getStatusCode().value());

    assertThat(statuses).containsOnlyOnce(200).filteredOn(s -> s != 200).containsOnly(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM vehicle_state_changes WHERE vehicle_id = ?"
                    + " AND to_state = 'IN_TRANSIT'",
                Integer.class,
                bike))
        .isEqualTo(1);
  }
}
