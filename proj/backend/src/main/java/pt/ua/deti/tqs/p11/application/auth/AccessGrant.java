package pt.ua.deti.tqs.p11.application.auth;

import java.util.UUID;

/** Result of a successful registration or login. */
public record AccessGrant(UUID userId, String accessToken) {}