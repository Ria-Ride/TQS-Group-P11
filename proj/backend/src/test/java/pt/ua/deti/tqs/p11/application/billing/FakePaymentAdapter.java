package pt.ua.deti.tqs.p11.application.billing;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory PaymentPort for core tests (architecture 4.3): answers what the test tells it to and
 * remembers what it was asked. Replacing the real gateway with it needs no change in the core.
 */
public final class FakePaymentAdapter implements PaymentPort {

  private final List<ChargeRequest> charges = new ArrayList<>();
  private ChargeResult nextAnswer = new ChargeResult.Approved("ch_fake");

  public FakePaymentAdapter answering(ChargeResult answer) {
    nextAnswer = answer;
    return this;
  }

  @Override
  public ChargeResult charge(ChargeRequest request) {
    charges.add(request);
    return nextAnswer;
  }

  /** Every charge asked for, in order. */
  public List<ChargeRequest> charges() {
    return List.copyOf(charges);
  }
}
