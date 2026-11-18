package pt.ua.deti.tqs.p11.adapter.in.web.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.auth.RegisterGuestCommand;
import pt.ua.deti.tqs.p11.application.auth.RegisterGuestUseCase;

/** Public authentication endpoints (US01). */
@RestController
@RequestMapping("/auth")
class AuthController {

  private final RegisterGuestUseCase registerGuest;

  AuthController(RegisterGuestUseCase registerGuest) {
    this.registerGuest = registerGuest;
  }

  /** Creates a guest account (BR10) and returns an access token for it. */
  @PostMapping("/guest")
  @ResponseStatus(HttpStatus.CREATED)
  AccessTokenResponse registerGuest(@Valid @RequestBody RegisterGuestRequest request) {
    return AccessTokenResponse.from(
        registerGuest.register(
            new RegisterGuestCommand(request.name(), request.email(), request.password())));
  }
}
