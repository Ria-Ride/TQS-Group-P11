package pt.ua.deti.tqs.p11.adapter.in.web.auth;

import jakarta.validation.constraints.NotBlank;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MaxUtf8Bytes;

/** Body of POST /auth/login. A malformed e-mail is not a 400: it simply matches no account. */
public record LoginRequest(
    @NotBlank String email,
    @NotBlank @MaxUtf8Bytes(RegisterGuestRequest.PASSWORD_MAX_BYTES) String password) {}
