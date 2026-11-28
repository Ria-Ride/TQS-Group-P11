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
import pt.ua.deti.tqs.p11.domain.booking.Settlement;

/**
 * BR13: settles the deposit of an ended ride. When there is an overtime charge, that much of the
 * deposit (at most all of it) is captured and the gateway gives the rest back in the same step;
 * when there is none, the whole deposit is released. Only a step the gateway did is recorded;
 * otherwise the deposit stays authorized and the failure is logged, so it can be settled by hand.
 */
public class DepositSettlement {

  private static final System.Logger LOG = System.getLogger(DepositSettlement.class.getName());

  private final PaymentRepository payments;
  private final PaymentPort gateway;
  private final Clock clock;

  public DepositSettlement(PaymentRepository payments, PaymentPort gateway, Clock clock) {
    this.payments = Objects.requireNonNull(payments, "payments");
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * @param completed a Completed booking, whose settlement is known
   * @throws IllegalArgumentException if the booking has not ended
   */
  public void settle(Booking completed) {
    Settlement settlement =
        completed
            .settlement()
            .orElseThrow(
                () -> new IllegalArgumentException("Booking " + completed.id() + " has not ended"));
    Optional<Payment> held =
        payments
            .findDepositOfBooking(completed.id())
            .filter(deposit -> deposit.state() == PaymentState.AUTHORIZED);
    if (held.isEmpty()) {
      LOG.log(Level.WARNING, "Booking {0} has no held deposit to settle", completed.id());
      return;
    }
    Payment deposit = held.get();
    boolean capture = !settlement.captured().isZero();
    DepositResult answer =
        capture
            ? gateway.capture(deposit.id(), settlement.captured())
            : gateway.release(deposit.id());
    if (!(answer instanceof DepositResult.Approved)) {
      LOG.log(
          Level.ERROR,
          "Deposit {0} of booking {1} is not settled ({2} {3}): the gateway answered {4}",
          deposit.id(),
          completed.id(),
          capture ? "capture" : "release",
          settlement.captured(),
          answer);
      return;
    }
    payments.save(
        capture
            ? Payment.depositCaptured(
                deposit.id(),
                deposit.accountId(),
                deposit.amount(),
                settlement.captured(),
                deposit.reference(),
                completed.id(),
                clock.instant())
            : Payment.depositReleased(
                deposit.id(),
                deposit.accountId(),
                deposit.amount(),
                deposit.reference(),
                completed.id(),
                clock.instant()));
  }
}
