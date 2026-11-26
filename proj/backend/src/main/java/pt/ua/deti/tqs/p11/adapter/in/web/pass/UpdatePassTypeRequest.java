package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MoneyFields;
import pt.ua.deti.tqs.p11.application.pass.UpdatePassTypeCommand;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * Body of PATCH /pass-types/{id} (US14, BR8): only the fields that are given change. The code is
 * not here because it never changes.
 */
record UpdatePassTypeRequest(
    @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 100) String name,
    @Min(1) @Max(PassTypeFields.MAX_VALIDITY_DAYS) Integer validityDays,
    @Schema(description = "Price before any discount, with at most two decimals", example = "45.50")
        @Pattern(regexp = MoneyFields.AMOUNT, message = MoneyFields.MESSAGE)
        String price,
    @Size(min = 1, message = "must allow at least one vehicle type")
        Set<VehicleType> vehicleTypes) {

  UpdatePassTypeCommand toCommand() {
    return new UpdatePassTypeCommand(
        name == null ? null : name.strip(),
        validityDays,
        price == null ? null : Money.of(price),
        vehicleTypes);
  }
}
