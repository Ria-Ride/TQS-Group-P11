package pt.ua.deti.tqs.p11.application.rules;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesChange;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;

/**
 * What the client manager does with the rules of use (US14, BR21). A change adds the next version
 * and leaves the old ones as they were, so a booking made under version 3 keeps version 3 (US04
 * stores the version it was created with). If two managers save at the same moment, the second is
 * told the version is taken instead of overwriting the first.
 */
public class RulesOfUseUseCase {

  private final RulesOfUseRepository rules;
  private final TransactionRunner transactions;
  private final Clock clock;

  public RulesOfUseUseCase(
      RulesOfUseRepository rules, TransactionRunner transactions, Clock clock) {
    this.rules = Objects.requireNonNull(rules, "rules");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** The rules now in force: the latest version. */
  public RulesOfUse current() {
    return rules
        .findCurrent()
        .orElseThrow(() -> new IllegalStateException("No rules of use are stored"));
  }

  /**
   * Makes the next version with the new values.
   *
   * @throws IllegalArgumentException if a value is outside its limits (BR21)
   * @throws RulesVersionConflictException if the rules changed meanwhile
   */
  public RulesOfUse change(RulesChange change, UUID by) {
    return transactions.inTransaction(
        () -> rules.save(current().revised(clock.instant(), by, change)));
  }

  /** Makes the next version with the deposit of one vehicle type changed. */
  public RulesOfUse setDeposit(VehicleType type, Money deposit, UUID by) {
    return transactions.inTransaction(
        () -> rules.save(current().withDeposit(clock.instant(), by, type, deposit)));
  }

  /** Every version, newest first. */
  public List<RulesOfUse> history() {
    return rules.findAll();
  }
}
