package pt.ua.deti.tqs.p11.adapter.in.web.admin;

import jakarta.validation.constraints.NotNull;

/** Body of PATCH /admin/users/{id}/status. A Boolean, so a missing value is a 400, not false. */
public record ChangeStatusRequest(@NotNull Boolean active) {}
