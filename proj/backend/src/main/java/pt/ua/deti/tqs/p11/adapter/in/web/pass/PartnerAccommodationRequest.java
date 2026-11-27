package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /partner-accommodations (US14, BR9): a partner hotel and its discount. */
record PartnerAccommodationRequest(
    @Schema(example = "Hotel Laguna") @NotBlank @Size(max = 100) String name,
    @Schema(description = "Discount on a pass for its guests, from 0 to 100", example = "10")
        @NotNull
        @Min(0)
        @Max(100)
        Integer discountPercent) {}
