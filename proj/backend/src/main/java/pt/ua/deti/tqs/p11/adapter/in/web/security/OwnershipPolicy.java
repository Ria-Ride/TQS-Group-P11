package pt.ua.deti.tqs.p11.adapter.in.web.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * BR23: an account's data is only for its owner. User-scoped endpoints use it through
 * {@code @PreAuthorize("@ownership.isSelf(#id, authentication)")}, so the rule lives here only.
 */
@Component("ownership")
public class OwnershipPolicy {

  public boolean isSelf(UUID userId, Authentication authentication) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}