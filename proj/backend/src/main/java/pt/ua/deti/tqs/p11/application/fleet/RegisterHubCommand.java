package pt.ua.deti.tqs.p11.application.fleet;

/** What an operator gives to register a hub (US08). Checked by the domain (Hub, GeoPosition). */
public record RegisterHubCommand(
    String code, String name, double latitude, double longitude, int capacity) {}
