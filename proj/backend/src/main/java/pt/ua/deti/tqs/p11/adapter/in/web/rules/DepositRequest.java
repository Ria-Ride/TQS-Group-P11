package pt.ua.deti.tqs.p11.adapter.in.web.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MoneyFields;

/** Body of PATCH /vehicle-types/{type}: the deposit held for a booking of that type (BR4). */
record DepositRequest(
    @Schema(description = "The deposit in euros, with at most two decimals", example = "20.00")
        @NotNull
        @Pattern(regexp = MoneyFields.AMOUNT, message = MoneyFields.MESSAGE)
        String depositAmount) {}
