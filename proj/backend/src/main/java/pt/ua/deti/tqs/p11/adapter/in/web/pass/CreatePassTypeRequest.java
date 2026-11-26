package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MoneyFields;
import pt.ua.deti.tqs.p11.application.pass.CreatePassTypeCommand;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** Body of POST /pass-types (US14, BR8). The code names the pass type and never changes. */
record CreatePassTypeRequest(
    @Schema(
            description = "Upper-case letters, digits and _, starting with a letter",
            example = "WEEK")
        @NotBlank
        @Pattern(
            regexp = "^[A-Z][A-Z0-9_]{0,31}$",
            message = "must be 1 to 32 upper-case letters, digits or _, starting with a letter")
        String code,
    @Schema(example = "Week pass") @NotBlank @Size(max = 100) String name,
    @Schema(description = "Days of 24 hours the pass is valid (BR8)", example = "7")
        @NotNull
        @Min(1)
        @Max(PassTypeFields.MAX_VALIDITY_DAYS)
        Integer validityDays,
    @Schema(
            description = "Price before any discount, in euros with at most two decimals",
            example = "40.00")
        @NotNull
        @Pattern(regexp = MoneyFields.AMOUNT, message = MoneyFields.MESSAGE)
        String price,
    @Schema(description = "The vehicle types the pass allows, at least one") @NotEmpty
        Set<VehicleType> vehicleTypes) {

  CreatePassTypeCommand toCommand() {
    return new CreatePassTypeCommand(
        code, name.strip(), validityDays, Money.of(price), vehicleTypes);
  }
}
