package pt.ua.deti.tqs.p11.application.auth;

/** Input of the guest registration use case. */
public record RegisterGuestCommand(String name, String email, String password) {}
