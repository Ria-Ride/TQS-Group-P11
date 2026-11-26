package pt.ua.deti.tqs.p11.application.billing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * In-memory PaymentPort for core tests (architecture 4.3): answers what the test tells it to and
 * remembers what it was asked. Replacing the real gateway with it needs no change in the core.
 */
public final class FakePaymentAdapter implements PaymentPort {

  /** A capture asked for: which deposit, and how much of it. */
  public record Capture(UUID paymentId, Money amount) {}

  private final List<ChargeRequest> charges = new ArrayList<>();
  private final List<DepositRequest> authorizations = new ArrayList<>();
  private final List<Capture> captures = new ArrayList<>();
  private final List<UUID> releases = new ArrayList<>();
  private ChargeResult nextAnswer = new ChargeResult.Approved("ch_fake");
  private DepositResult nextAuthorization = new DepositResult.Approved("au_fake");
  private DepositResult nextCapture = new DepositResult.Approved("au_fake");
  private DepositResult nextRelease = new DepositResult.Approved("au_fake");

  public FakePaymentAdapter answering(ChargeResult answer) {
    nextAnswer = answer;
    return this;
  }

  public FakePaymentAdapter answeringAuthorizations(DepositResult answer) {
    nextAuthorization = answer;
    return this;
  }

  public FakePaymentAdapter answeringCaptures(DepositResult answer) {
    nextCapture = answer;
    return this;
  }

  public FakePaymentAdapter answeringReleases(DepositResult answer) {
    nextRelease = answer;
    return this;
  }

  @Override
  public ChargeResult charge(ChargeRequest request) {
    charges.add(request);
    return nextAnswer;
  }

  @Override
  public DepositResult authorize(DepositRequest request) {
    authorizations.add(request);
    return nextAuthorization;
  }

  @Override
  public DepositResult capture(UUID paymentId, Money amount) {
    captures.add(new Capture(paymentId, amount));
    return nextCapture;
  }

  @Override
  public DepositResult release(UUID paymentId) {
    releases.add(paymentId);
    return nextRelease;
  }

  /** Every charge asked for, in order. */
  public List<ChargeRequest> charges() {
    return List.copyOf(charges);
  }

  /** Every deposit authorization asked for, in order. */
  public List<DepositRequest> authorizations() {
    return List.copyOf(authorizations);
  }

  /** Every capture asked for, in order. */
  public List<Capture> captures() {
    return List.copyOf(captures);
  }

  /** The payment id of every release asked for, in order. */
  public List<UUID> releases() {
    return List.copyOf(releases);
  }
}