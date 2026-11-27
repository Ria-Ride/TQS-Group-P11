package pt.ua.deti.tqs.p11.gateway;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** How much of a deposit to take; the rest is given back. */
public record CaptureRequest(
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount) {}