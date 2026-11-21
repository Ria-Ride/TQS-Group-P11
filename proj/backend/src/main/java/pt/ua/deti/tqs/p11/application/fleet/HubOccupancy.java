package pt.ua.deti.tqs.p11.application.fleet;

import pt.ua.deti.tqs.p11.domain.fleet.Hub;

/** A hub with how many vehicles it holds now (BR14), as operators see it. */
public record HubOccupancy(Hub hub, int vehiclesHeld) {}
