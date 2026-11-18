package pt.ua.deti.tqs.p11.adapter.in.web.auth;

/** Body of POST /auth/guest. */
public record RegisterGuestRequest(String name, String email, String password) {}