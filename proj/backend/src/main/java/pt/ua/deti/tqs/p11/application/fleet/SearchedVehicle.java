package pt.ua.deti.tqs.p11.application.fleet;

import pt.ua.deti.tqs.p11.domain.fleet.Bookability;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;

/**
 * A vehicle of a hub, whether it can be booked now, and the battery in percent that the ride asked
 * for needs for this vehicle (BR1). It can be above 100 for a ride no battery can do.
 */
public record SearchedVehicle(Vehicle vehicle, Bookability bookability, int requiredPercent) {}
