package pt.ua.deti.tqs.p11.adapter.in.web.user;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.user.GetUserProfileUseCase;

/** User-scoped endpoints. Each one is guarded by the ownership policy (BR23). */
@RestController
@RequestMapping("/users")
class UserController {

  private final GetUserProfileUseCase profiles;

  UserController(GetUserProfileUseCase profiles) {
    this.profiles = profiles;
  }

  @GetMapping("/{id}")
  @PreAuthorize("@ownership.isSelf(#id, authentication) or hasRole('ADMIN')")
  UserProfileResponse get(@PathVariable UUID id) {
    return UserProfileResponse.from(profiles.get(id));
  }
}
