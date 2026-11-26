package pt.ua.deti.tqs.p11.gateway;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decides and remembers deposits, one per idempotency key. The test card decides the authorization;
 * capture and release then move it along. Every step is safe to repeat: the same request gets the
 * same answer, and a step that no longer fits is refused. The methods are synchronized, so two
 * requests for the same deposit never interleave.
 */
@Component
public class AuthorizationBook {

  /** The authorization request and the deposit as it stands now, per idempotency key. */
  private record Entry(AuthorizationRequest request, Authorization authorization) {}

  private final Map<String, Entry> byKey = new HashMap<>();

  /**
   * @throws IdempotencyKeyReusedException if the key was used for a different request
   */
  public synchronized Authorization authorize(AuthorizationRequest request) {
    Entry entry =
        byKey.computeIfAbsent(request.idempotencyKey(), key -> new Entry(request, decide(request)));
    if (!sameRequest(entry.request(), request)) {
      throw new IdempotencyKeyReusedException(request.idempotencyKey());
    }
    return entry.authorization();
  }

  /**
   * Takes the amount and gives back the rest. Capturing the same amount again returns the same
   * capture.
   *
   * @throws AuthorizationNotFoundException if no deposit has the key
   * @throws CaptureExceedsAuthorizationException if the amount is more than the deposit holds
   * @throws AuthorizationStateConflictException if the deposit was declined, released, or captured
   *     for another amount
   */
  public synchronized Authorization capture(String key, BigDecimal amount) {
    Entry entry = find(key);
    Authorization current = entry.authorization();
    return switch (current.status()) {
      case AUTHORIZED -> {
        if (amount.compareTo(current.amount()) > 0) {
          throw new CaptureExceedsAuthorizationException(key, amount, current.amount());
        }
        yield store(entry, current.capturing(amount));
      }
      case CAPTURED -> {
        if (current.captured().compareTo(amount) != 0) {
          throw new AuthorizationStateConflictException(
              key, current.status(), "capture another amount");
        }
        yield current;
      }
      case DECLINED, RELEASED ->
          throw new AuthorizationStateConflictException(key, current.status(), "be captured");
    };
  }

  /**
   * Gives the whole amount back. A deposit that was declined or already released holds nothing,
   * so it is returned as it is.
   *
   * @throws AuthorizationNotFoundException if no deposit has the key
   * @throws AuthorizationStateConflictException if the deposit was captured
   */
  public synchronized Authorization release(String key) {
    Entry entry = find(key);
    Authorization current = entry.authorization();
    return switch (current.status()) {
      case AUTHORIZED -> store(entry, current.releasing());
      case DECLINED, RELEASED -> current;
      case CAPTURED ->
          throw new AuthorizationStateConflictException(key, current.status(), "be released");
    };
  }

  /** How many deposits are remembered. */
  public synchronized int size() {
    return byKey.size();
  }

  private Entry find(String key) {
    Entry entry = byKey.get(key);
    if (entry == null) {
      throw new AuthorizationNotFoundException(key);
    }
    return entry;
  }

  private Authorization store(Entry entry, Authorization next) {
    byKey.put(next.idempotencyKey(), new Entry(entry.request(), next));
    return next;
  }

  private static boolean sameRequest(AuthorizationRequest first, AuthorizationRequest again) {
    return first.amount().compareTo(again.amount()) == 0
        && first.currency().equals(again.currency())
        && first.token().equals(again.token());
  }

  private static Authorization decide(AuthorizationRequest request) {
    String id = "au_" + UUID.randomUUID();
    return TestCard.of(request.token())
        .map(
            card ->
                switch (card) {
                  case APPROVE -> authorization(id, request, AuthorizationStatus.AUTHORIZED, null, false);
                  case DECLINE ->
                      authorization(id, request, AuthorizationStatus.DECLINED, "card_declined", false);
                  case TIMEOUT -> authorization(id, request, AuthorizationStatus.AUTHORIZED, null, true);
                })
        .orElseGet(
            () -> authorization(id, request, AuthorizationStatus.DECLINED, "invalid_token", false));
  }

  private static Authorization authorization(
      String id,
      AuthorizationRequest request,
      AuthorizationStatus status,
      String reason,
      boolean late) {
    return new Authorization(
        id,
        request.idempotencyKey(),
        request.amount(),
        request.currency(),
        status,
        null,
        reason,
        late);
  }
}