package pt.ua.deti.tqs.p11.application.booking;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import pt.ua.deti.tqs.p11.application.billing.DepositResult;
import pt.ua.deti.tqs.p11.application.billing.PaymentPort;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.billing.PaymentState;
import pt.ua.deti.tqs.p11.domain.booking.Booking;

/**
 * BR13: gives the whole deposit of a booking back, for every way a booking ends without a ride
 * (cancelled, expired, cancelled by a vehicle fault). Only a release that went through is recorded;
 * otherwise the deposit stays authorized and the failure is logged, so it can be released by hand.
 * A booking with no held deposit (already given back) is left as it is, so giving back twice is
 * safe.
 */
public class DepositReturn {

  private static final System.Logger LOG = System.getLogger(DepositReturn.class.getName());

  private final PaymentRepository payments;
  private final PaymentPort gateway;
  private final Clock clock;

  public DepositReturn(PaymentRepository payments, PaymentPort gateway, Clock clock) {
    this.payments = Objects.requireNonNull(payments, "payments");
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public void giveBack(Booking booking) {
    Optional<Payment> held =
        payments
            .findDepositOfBooking(booking.id())
            .filter(deposit -> deposit.state() == PaymentState.AUTHORIZED);
    if (held.isEmpty()) {
      LOG.log(Level.WARNING, "Booking {0} has no held deposit to give back", booking.id());
      return;
    }
    Payment deposit = held.get();
    DepositResult released = gateway.release(deposit.id());
    if (released instanceof DepositResult.Approved) {
      payments.save(
          Payment.depositReleased(
              deposit.id(),
              deposit.accountId(),
              deposit.amount(),
              deposit.reference(),
              booking.id(),
              clock.instant()));
    } else {
      LOG.log(
          Level.ERROR,
          "Deposit {0} of booking {1} may still be held: the release answered {2}",
          deposit.id(),
          booking.id(),
          released);
    }
  }
}
