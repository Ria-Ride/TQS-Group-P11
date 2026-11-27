package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of POST /bookings/{id}/end (US06): where the ride ends. */
record EndUseRequest(
    @Schema(
            description =
                "The hub where the vehicle is left; any hub with a free slot, not only the one the"
                    + " ride started from")
        @NotNull
        UUID hubId) {}
