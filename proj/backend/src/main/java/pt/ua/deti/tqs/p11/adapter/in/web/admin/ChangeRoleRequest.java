package pt.ua.deti.tqs.p11.adapter.in.web.admin;

import jakarta.validation.constraints.NotNull;
import pt.ua.deti.tqs.p11.domain.user.Role;

/** Body of PATCH /admin/users/{id}/role. */
public record ChangeRoleRequest(@NotNull Role role, Boolean fieldTechnician) {

  /** Left out of the JSON means not a technician (Jackson 3 refuses null for a primitive). */
  public ChangeRoleRequest {
    fieldTechnician = Boolean.TRUE.equals(fieldTechnician);
  }
}
