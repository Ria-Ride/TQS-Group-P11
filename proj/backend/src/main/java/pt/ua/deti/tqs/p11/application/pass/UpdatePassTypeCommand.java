package pt.ua.deti.tqs.p11.application.pass;

import java.util.Set;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** What the client manager changes in a pass type (US14); a null field stays as it is. */
public record UpdatePassTypeCommand(
    String name, Integer validityDays, Money basePrice, Set<VehicleType> vehicleTypes) {}
