package pt.ua.deti.tqs.p11.gateway;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Decides and remembers charges, one per idempotency key. The test card decides the answer. A
 * request repeated with the same key gets the first charge back, so a caller that retries after a
 * lost answer is never charged twice.
 */
@Component
public class ChargeBook {

  /** The request and the charge it got, per idempotency key. */
  private record Entry(ChargeRequest request, Charge charge) {}

  private final Map<String, Entry> byKey = new ConcurrentHashMap<>();

  /**
   * @throws IdempotencyKeyReusedException if the key was used for a different request
   */
  public Charge charge(ChargeRequest request) {
    Entry entry =
        byKey.computeIfAbsent(request.idempotencyKey(), key -> new Entry(request, decide(request)));
    if (!sameCharge(entry.request(), request)) {
      throw new IdempotencyKeyReusedException(request.idempotencyKey());
    }
    return entry.charge();
  }

  /** How many charges are remembered. */
  public int size() {
    return byKey.size();
  }

  private static boolean sameCharge(ChargeRequest first, ChargeRequest again) {
    return first.amount().compareTo(again.amount()) == 0
        && first.currency().equals(again.currency())
        && first.token().equals(again.token());
  }

  private static Charge decide(ChargeRequest request) {
    String id = "ch_" + UUID.randomUUID();
    return TestCard.of(request.token())
        .map(
            card ->
                switch (card) {
                  case APPROVE -> charge(id, request, ChargeStatus.APPROVED, null, false);
                  case DECLINE ->
                      charge(id, request, ChargeStatus.DECLINED, "card_declined", false);
                  case TIMEOUT -> charge(id, request, ChargeStatus.DECLINED, "timeout", true);
                })
        .orElseGet(() -> charge(id, request, ChargeStatus.DECLINED, "invalid_token", false));
  }

  private static Charge charge(
      String id, ChargeRequest request, ChargeStatus status, String reason, boolean late) {
    return new Charge(
        id, request.idempotencyKey(), request.amount(), request.currency(), status, reason, late);
  }
}
