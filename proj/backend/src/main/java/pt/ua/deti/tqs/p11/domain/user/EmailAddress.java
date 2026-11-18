package pt.ua.deti.tqs.p11.domain.user;

/** An e-mail address, normalized so the same address cannot register twice. */
public record EmailAddress(String value) {}