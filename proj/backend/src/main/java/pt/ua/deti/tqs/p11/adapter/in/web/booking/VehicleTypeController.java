package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.booking.UsageRulesPort;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.booking.UsageRules;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * The vehicle types and what a booking of each one holds as deposit (BR11), read from the rules of
 * use in force. Every logged-in role reads it (access matrix). The client manager will change the
 * deposits here (PATCH /vehicle-types/{type}, US14).
 */
@RestController
@Tag(name = "Vehicle types", description = "Vehicle types and their deposits")
class VehicleTypeController {

  private final UsageRulesPort usageRules;

  VehicleTypeController(UsageRulesPort usageRules) {
    this.usageRules = usageRules;
  }

  @GetMapping("/vehicle-types")
  @Operation(summary = "Every vehicle type with the deposit a booking of it holds")
  List<VehicleTypeResponse> list() {
    UsageRules rules = usageRules.current();
    return Arrays.stream(VehicleType.values())
        .map(type ->
            new VehicleTypeResponse(
                type.name(), rules.forType(type).deposit().toString(), Money.CURRENCY))
        .toList();
  }
}
