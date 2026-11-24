package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.adapter.in.web.InvalidFieldException;
import pt.ua.deti.tqs.p11.application.fleet.HubManagementUseCase;
import pt.ua.deti.tqs.p11.application.fleet.SearchedVehicle;
import pt.ua.deti.tqs.p11.application.fleet.VehicleSearchUseCase;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryRule;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * Hubs (US08) and the search of their vehicles (US02). Who may call each endpoint is in
 * SecurityConfiguration and the access matrix (BR23): every role reads, operators register,
 * operators and client managers change capacity.
 */
@RestController
@RequestMapping("/hubs")
class HubController {

  private final HubManagementUseCase hubs;
  private final VehicleSearchUseCase search;

  HubController(HubManagementUseCase hubs, VehicleSearchUseCase search) {
    this.hubs = hubs;
    this.search = search;
  }

  @GetMapping
  List<HubResponse> list() {
    return hubs.list().stream().map(HubResponse::from).toList();
  }

  @GetMapping("/{id}")
  HubResponse get(@PathVariable UUID id) {
    return HubResponse.from(hubs.get(id));
  }

  /**
   * US02: the vehicles at the hub, each with whether it can be booked, optionally of one type and
   * for a ride of a given length in minutes (BR1). A hub with no vehicle gives an empty list, not
   * an error.
   */
  @GetMapping("/{id}/vehicles")
  List<HubVehicleResponse> vehicles(
      @PathVariable UUID id,
      @RequestParam(required = false) VehicleType type,
      @RequestParam(required = false) Integer minutes) {
    if (minutes != null && (minutes < 1 || minutes > BatteryRule.MAX_MINUTES)) {
      throw new InvalidFieldException(
          "minutes", "must be from 1 to %d".formatted(BatteryRule.MAX_MINUTES));
    }
    List<SearchedVehicle> found = search.search(id, type, minutes);
    return found.stream().map(HubVehicleResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  HubResponse register(@Valid @RequestBody RegisterHubRequest request) {
    return HubResponse.from(hubs.register(request.toCommand()));
  }

  @PatchMapping("/{id}")
  HubResponse changeCapacity(
      @PathVariable UUID id, @Valid @RequestBody ChangeCapacityRequest request) {
    return HubResponse.from(hubs.changeCapacity(id, request.capacity()));
  }
}
