package pt.ua.deti.tqs.p11.application.fleet;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** What an operator gives to register a vehicle (US08): its code, type and hub. */
public record RegisterVehicleCommand(String code, VehicleType type, UUID hubId) {}
