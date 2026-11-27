package pt.ua.deti.tqs.p11.application.rules;

import java.util.List;
import java.util.Optional;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;

/** Port: where the versions of the rules of use are kept. Versions are only ever added. */
public interface RulesOfUseRepository {

  /** The latest version. */
  Optional<RulesOfUse> findCurrent();

  /** Every version, newest first. */
  List<RulesOfUse> findAll();

  /**
   * Adds a version.
   *
   * @throws RulesVersionConflictException if that version number is already stored
   */
  RulesOfUse save(RulesOfUse rules);
}
