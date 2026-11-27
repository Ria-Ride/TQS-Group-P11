package pt.ua.deti.tqs.p11.application.pass;

import java.util.Set;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** What the client manager gives to create a pass type (US14). */
public record CreatePassTypeCommand(
    String code, String name, int validityDays, Money basePrice, Set<VehicleType> vehicleTypes) {}
