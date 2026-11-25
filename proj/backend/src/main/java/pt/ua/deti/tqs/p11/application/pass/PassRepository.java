package pt.ua.deti.tqs.p11.application.pass;

import java.util.List;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.pass.Pass;

/** Port: where issued passes are stored. */
public interface PassRepository {

  Pass save(Pass pass);

  /** The passes of the account, newest first. */
  List<Pass> findByAccount(UUID accountId);
}
