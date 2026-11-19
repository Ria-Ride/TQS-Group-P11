package pt.ua.deti.tqs.p11.adapter.in.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.AccountFields;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MaxUtf8Bytes;

/** Body of POST /auth/guest. Format rules live here, at the API boundary (see AccountFields). */
public record RegisterGuestRequest(
    @NotBlank @Size(max = RegisterGuestRequest.NAME_MAX) String name,
    @NotBlank @Email @Pattern(regexp = RegisterGuestRequest.DOTTED_DOMAIN) String email,
    @NotNull
        @Size(min = RegisterGuestRequest.PASSWORD_MIN)
        @MaxUtf8Bytes(RegisterGuestRequest.PASSWORD_MAX_BYTES)
        String password) {

  static final int NAME_MAX = AccountFields.NAME_MAX;
  static final int PASSWORD_MIN = AccountFields.PASSWORD_MIN;
  static final int PASSWORD_MAX_BYTES = AccountFields.PASSWORD_MAX_BYTES;
  static final String DOTTED_DOMAIN = AccountFields.DOTTED_DOMAIN;
}
