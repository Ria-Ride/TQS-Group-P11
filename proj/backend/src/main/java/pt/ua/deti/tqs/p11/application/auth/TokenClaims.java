package pt.ua.deti.tqs.p11.application.auth;

import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.Role;

/** What a valid access token says about its holder. */
public record TokenClaims(UUID userId, Role role, Instant expiresAt) {}
