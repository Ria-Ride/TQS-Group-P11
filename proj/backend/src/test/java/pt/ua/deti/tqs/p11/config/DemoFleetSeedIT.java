package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;

/**
 * The demo fleet (the hubs and vehicles of the simulator's default scenario), registered at
 * start-up when app.demo.seed-fleet is on, so that the simulator's readings are about vehicles the
 * platform knows (US09). Registered through the same use cases as the API, so BR14 and BR16 hold.
 */
@SpringBootTest(properties = "app.demo.seed-fleet=true")
@Import(TestcontainersConfiguration.class)
class DemoFleetSeedIT {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationRunner demoFleet;

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  @Test
  void startUp_registersTheHubsAndVehiclesOfTheScenario_andActivatesThem() {
    assertThat(count("hubs")).isEqualTo(6);
    assertThat(count("vehicles")).isEqualTo(20);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM vehicles WHERE state = 'AVAILABLE'", Integer.class))
        .isEqualTo(20);
    assertThat(
            jdbc.queryForObject(
                "SELECT h.code FROM vehicles v JOIN hubs h ON h.id = v.hub_id "
                    + "WHERE v.code = 'bike-0001'",
                String.class))
        .isEqualTo("hub-05");
  }

  @Test
  void runningItAgain_registersNothingTwice() throws Exception {
    demoFleet.run(null);
    demoFleet.run(null);

    assertThat(count("hubs")).isEqualTo(6);
    assertThat(count("vehicles")).isEqualTo(20);
  }
}
