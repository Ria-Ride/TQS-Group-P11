package pt.ua.deti.tqs.p11.application.booking;

import java.util.List;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.booking.PassCoverage;

/**
 * Port: the tourist's Passes as the booking rules need them (BR4): validity and allowed vehicle
 * types. Provided by the passes module, so bookings do not depend on how passes are stored.
 */
@FunctionalInterface
public interface PassCoverages {

  List<PassCoverage> ofAccount(UUID accountId);
}
