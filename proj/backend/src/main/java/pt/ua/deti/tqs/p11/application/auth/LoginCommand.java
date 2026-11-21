package pt.ua.deti.tqs.p11.application.auth;

/** Input of the login use case. */
public record LoginCommand(String email, String password) {}
