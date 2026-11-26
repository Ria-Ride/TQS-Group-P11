package pt.ua.deti.tqs.p11.application.booking;

import pt.ua.deti.tqs.p11.domain.booking.UsageRules;

/**
 * Port: the client's current rules of use for bookings (maximum duration, deposits, start window,
 * overtime). Read from configuration until US14, whose adapter reads the rules the client edits.
 * The booking use cases ask for them on every booking, so a change applies to the next one.
 */
@FunctionalInterface
public interface UsageRulesPort {

  UsageRules current();
}
