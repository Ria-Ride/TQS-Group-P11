package pt.ua.deti.tqs.p11.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.application.fleet.HubManagementUseCase;
import pt.ua.deti.tqs.p11.application.fleet.HubOccupancy;
import pt.ua.deti.tqs.p11.application.fleet.RegisterHubCommand;
import pt.ua.deti.tqs.p11.application.fleet.RegisterVehicleCommand;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import tools.jackson.databind.json.JsonMapper;

/**
 * The demo fleet: the hubs and vehicles of the simulator's default scenario (ria-ride.yml,
 * fleet-01), registered and activated at start-up when app.demo.seed-fleet is true, so that the
 * readings of the simulator are about vehicles the platform knows (US09, BR17). Off by default;
 * Docker Compose turns it on. It uses the same use cases as the API, so BR14 and BR16 hold, and it
 * skips what already exists, so a restart adds nothing. src/main/resources/demo/fleet.json is
 * copied from the scenario: when the simulator's scenario changes, change it too.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.demo", name = "seed-fleet", havingValue = "true")
class DemoFleetConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(DemoFleetConfiguration.class);
  private static final String RESOURCE = "/demo/fleet.json";

  /** The file, as read. */
  record DemoFleet(List<DemoHub> hubs, List<DemoVehicle> vehicles) {}

  record DemoHub(String code, String name, double latitude, double longitude, int capacity) {}

  record DemoVehicle(String code, VehicleType type, String hub) {}

  @Bean
  ApplicationRunner demoFleet(
      HubManagementUseCase hubs, VehicleLifecycleUseCase vehicles, VehicleRepository stored)
      throws IOException {
    DemoFleet fleet = read();
    return args -> {
      Map<String, UUID> hubIds =
          hubs.list().stream()
              .map(HubOccupancy::hub)
              .collect(Collectors.toMap(hub -> hub.code().value(), hub -> hub.id()));
      int newHubs = 0;
      for (DemoHub hub : fleet.hubs()) {
        if (!hubIds.containsKey(hub.code())) {
          HubOccupancy created =
              hubs.register(
                  new RegisterHubCommand(
                      hub.code(), hub.name(), hub.latitude(), hub.longitude(), hub.capacity()));
          hubIds.put(hub.code(), created.hub().id());
          newHubs++;
        }
      }
      int newVehicles = 0;
      for (DemoVehicle vehicle : fleet.vehicles()) {
        if (!stored.existsByCode(FleetCode.of(vehicle.code()))) {
          UUID id =
              vehicles
                  .register(
                      new RegisterVehicleCommand(
                          vehicle.code(), vehicle.type(), hubIds.get(vehicle.hub())))
                  .id();
          vehicles.activate(id);
          newVehicles++;
        }
      }
      LOG.info("Demo fleet: {} hubs and {} vehicles registered", newHubs, newVehicles);
    };
  }

  private static DemoFleet read() throws IOException {
    try (InputStream in = DemoFleetConfiguration.class.getResourceAsStream(RESOURCE)) {
      return JsonMapper.builder().build().readValue(in, DemoFleet.class);
    }
  }
}
