package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.adapter.in.web.InvalidFieldException;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;

/**
 * Vehicles and their lifecycle (US08). Every role reads vehicles; operators register them, change
 * their state and read their history (BR23, see SecurityConfiguration). The rules live in the use
 * case and the domain; this class only maps HTTP to them.
 */
@RestController
@RequestMapping("/vehicles")
class VehicleController {

  private final VehicleLifecycleUseCase vehicles;

  VehicleController(VehicleLifecycleUseCase vehicles) {
    this.vehicles = vehicles;
  }

  @GetMapping
  List<VehicleResponse> list() {
    return vehicles.list().stream().map(VehicleResponse::from).toList();
  }

  @GetMapping("/{id}")
  VehicleResponse get(@PathVariable UUID id) {
    return VehicleResponse.from(vehicles.get(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  VehicleResponse register(@Valid @RequestBody RegisterVehicleRequest request) {
    return VehicleResponse.from(vehicles.register(request.toCommand()));
  }

  /** One endpoint for every operator action, as in the access matrix. */
  @PostMapping("/{id}/transitions")
  VehicleResponse transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
    request.checkHubId();
    Vehicle vehicle =
        switch (request.action()) {
          case ACTIVATE -> vehicles.activate(id);
          case DEACTIVATE -> vehicles.deactivate(id);
          case START_MOVE -> vehicles.startMove(id);
          case CONFIRM_ARRIVAL -> vehicles.confirmArrival(id, request.hubId());
          case RETIRE -> vehicles.retire(id);
          // US04: only a booking starts a use, after its deposit is held (BR11).
          case START_USE ->
              throw new InvalidFieldException(
                  "action", "is started by a booking once its deposit is held, not by an operator");
          // US05: a reservation holds and gives back the vehicle; the booking decides when.
          case RESERVE, RELEASE, END_USE ->
              throw new InvalidFieldException(
                  "action", "is done by a booking (US05, US06), not by an operator");
          // BR18: a fault reaches Maintenance only with its ticket (telemetry or POST /tickets).
          case REPORT_FAULT ->
              throw new InvalidFieldException(
                  "action", "is reported by telemetry or a maintenance ticket, not by an operator");
          // BR20: a vehicle leaves Maintenance when its last open ticket is closed (US11).
          case RETURN_TO_SERVICE ->
              throw new InvalidFieldException(
                  "action", "happens when the vehicle's last open ticket is closed");
        };
    return VehicleResponse.from(vehicle);
  }

  @GetMapping("/{id}/history")
  List<VehicleStateChangeResponse> history(@PathVariable UUID id) {
    return vehicles.history(id).stream().map(VehicleStateChangeResponse::from).toList();
  }
}
