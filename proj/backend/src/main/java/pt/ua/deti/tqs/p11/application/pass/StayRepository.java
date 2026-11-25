package pt.ua.deti.tqs.p11.application.pass;

import java.util.Optional;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/** Port: where the stays of partner hotels, and the account each code is linked to, are stored. */
public interface StayRepository {

  /**
   * Finds the stay with this code (stored upper-case) and locks it until the current transaction
   * ends, so two purchases with the same code run one after the other and only one account can link
   * it (BR9, K3). Must be called inside a transaction.
   */
  Optional<Stay> findByCodeForUpdate(String code);

  /** Finds the stay with this code (stored upper-case), without locking it. */
  Optional<Stay> findByCode(String code);

  /** Stores the account the code is linked to; the rest of a stay does not change here. */
  void save(Stay stay);
}
