package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of PATCH /hubs/{id}: the new capacity (BR14). */
public record ChangeCapacityRequest(@NotNull @Min(1) Integer capacity) {}
