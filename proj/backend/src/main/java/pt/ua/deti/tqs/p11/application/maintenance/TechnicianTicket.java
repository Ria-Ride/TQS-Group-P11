package pt.ua.deti.tqs.p11.application.maintenance;

import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;

/**
 * One ticket of a technician's list (US11), with where to find the vehicle: the hub it is at
 * (hubCode and hubName, null when it is at no hub) and its last known position (null before the
 * first reading with one). breached is BR19 at the time the list was read.
 */
public record TechnicianTicket(
    MaintenanceTicket ticket,
    String vehicleCode,
    String hubCode,
    String hubName,
    GeoPosition lastPosition,
    boolean breached) {}
