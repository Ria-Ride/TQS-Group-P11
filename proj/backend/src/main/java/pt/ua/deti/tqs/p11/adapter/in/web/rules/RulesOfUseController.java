package pt.ua.deti.tqs.p11.adapter.in.web.rules;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.rules.RulesOfUseUseCase;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;

/**
 * The rules of use and the deposits (US14, BR21). Every role reads the rules in force; only the
 * client manager (and the admin) changes them (SecurityConfiguration and the access matrix, BR23).
 * A change never edits a version: it makes the next one.
 */
@RestController
@Tag(name = "Rules of use", description = "Rules of use and deposits, versioned (US14)")
class RulesOfUseController {

  private final RulesOfUseUseCase rules;

  RulesOfUseController(RulesOfUseUseCase rules) {
    this.rules = rules;
  }

  @GetMapping("/usage-rules")
  @Operation(summary = "The rules of use now in force: the latest version")
  RulesOfUseResponse current() {
    return RulesOfUseResponse.from(rules.current());
  }

  @GetMapping("/usage-rules/versions")
  @Operation(summary = "Every version of the rules of use, newest first")
  List<RulesOfUseResponse> versions() {
    return rules.history().stream().map(RulesOfUseResponse::from).toList();
  }

  @PutMapping("/usage-rules")
  @Operation(
      summary = "Change the rules of use",
      description =
          "Makes the next version with these values; the deposits are kept. The old versions stay"
              + " as they were, so the change only applies to bookings created afterwards"
              + " (BR21).")
  @ApiResponse(responseCode = "200", description = "The new version")
  @ApiResponse(
      responseCode = "400",
      description = "A value is outside its limits (BR21); nothing is saved")
  @ApiResponse(
      responseCode = "409",
      description = "Someone saved the rules at the same moment; read them again and retry")
  RulesOfUseResponse change(@Valid @RequestBody RulesOfUseRequest request, Authentication caller) {
    return RulesOfUseResponse.from(rules.change(request.toChange(), accountOf(caller)));
  }

  @GetMapping("/vehicle-types")
  @Operation(summary = "The vehicle types with the deposit and the battery rate now in force")
  List<VehicleTypeResponse> vehicleTypes() {
    RulesOfUse current = rules.current();
    return Arrays.stream(VehicleType.values())
        .map(type -> VehicleTypeResponse.of(current, type))
        .toList();
  }

  @PatchMapping("/vehicle-types/{type}")
  @Operation(
      summary = "Change the deposit of a vehicle type",
      description = "Makes the next version of the rules of use with that deposit (BR4, BR21).")
  @ApiResponse(responseCode = "400", description = "The deposit is not a valid amount")
  VehicleTypeResponse changeDeposit(
      @PathVariable VehicleType type,
      @Valid @RequestBody DepositRequest request,
      Authentication caller) {
    RulesOfUse next = rules.setDeposit(type, Money.of(request.depositAmount()), accountOf(caller));
    return VehicleTypeResponse.of(next, type);
  }

  /** The access token's subject is the account id (ActiveAccountAuthenticationConverter). */
  private static UUID accountOf(Authentication caller) {
    return UUID.fromString(caller.getName());
  }
}
