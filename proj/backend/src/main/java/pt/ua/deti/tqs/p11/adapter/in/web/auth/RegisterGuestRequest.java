package pt.ua.deti.tqs.p11.adapter.in.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MaxUtf8Bytes;

/** Body of POST /auth/guest. Format rules live here, at the API boundary. */
public record RegisterGuestRequest(
    @NotBlank @Size(max = RegisterGuestRequest.NAME_MAX) String name,
    @NotBlank @Email @Pattern(regexp = RegisterGuestRequest.DOTTED_DOMAIN) String email,
    @NotNull
        @Size(min = RegisterGuestRequest.PASSWORD_MIN)
        @MaxUtf8Bytes(RegisterGuestRequest.PASSWORD_MAX_BYTES)
        String password) {

  static final int NAME_MAX = 100;
  static final int PASSWORD_MIN = 8;

  /** BCrypt only uses the first 72 bytes of a password (not characters). */
  static final int PASSWORD_MAX_BYTES = 72;

  /**
   * @Email accepts "ana@localhost"; we also require a dot in the domain and no spaces.
   */
  static final String DOTTED_DOMAIN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
}
