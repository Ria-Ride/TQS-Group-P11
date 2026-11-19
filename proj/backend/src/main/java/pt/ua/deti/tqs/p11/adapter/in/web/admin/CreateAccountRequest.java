package pt.ua.deti.tqs.p11.adapter.in.web.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.AccountFields;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MaxUtf8Bytes;
import pt.ua.deti.tqs.p11.application.user.CreateAccountCommand;
import pt.ua.deti.tqs.p11.domain.user.Role;

/**
 * Body of POST /admin/users. Same field rules as guest registration; fieldTechnician is optional.
 */
public record CreateAccountRequest(
    @NotBlank @Size(max = AccountFields.NAME_MAX) String name,
    @NotBlank @Email @Pattern(regexp = AccountFields.DOTTED_DOMAIN) String email,
    @NotNull @Size(min = AccountFields.PASSWORD_MIN) @MaxUtf8Bytes(AccountFields.PASSWORD_MAX_BYTES)
        String password,
    @NotNull Role role,
    Boolean fieldTechnician) {

  /** Left out of the JSON means not a technician (Jackson 3 refuses null for a primitive). */
  public CreateAccountRequest {
    fieldTechnician = Boolean.TRUE.equals(fieldTechnician);
  }

  CreateAccountCommand toCommand() {
    return new CreateAccountCommand(name, email, password, role, fieldTechnician);
  }

  /** Keeps the password out of logs and error messages. */
  @Override
  public String toString() {
    return "CreateAccountRequest[name=" + name + ", email=" + email + ", role=" + role + "]";
  }
}
