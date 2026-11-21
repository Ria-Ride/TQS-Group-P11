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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.fleet.HubManagementUseCase;

/**
 * Hubs (US08). Who may call each endpoint is in SecurityConfiguration and the access matrix (BR23):
 * every role reads, operators register, operators and client managers change capacity.
 */
@RestController
@RequestMapping("/hubs")
class HubController {

  private final HubManagementUseCase hubs;

  HubController(HubManagementUseCase hubs) {
    this.hubs = hubs;
  }

  @GetMapping
  List<HubResponse> list() {
    return hubs.list().stream().map(HubResponse::from).toList();
  }

  @GetMapping("/{id}")
  HubResponse get(@PathVariable UUID id) {
    return HubResponse.from(hubs.get(id));
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
