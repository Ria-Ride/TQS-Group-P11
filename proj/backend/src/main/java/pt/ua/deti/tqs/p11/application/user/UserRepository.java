package pt.ua.deti.tqs.p11.application.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.User;

/** Port: where user accounts are stored. */
public interface UserRepository {

  boolean existsByEmail(EmailAddress email);

  Optional<User> findByEmail(EmailAddress email);

  User save(User user);

  Optional<User> findById(UUID id);

  List<User> findAll();

  /**
   * Finds the account and locks it until the current transaction ends, so two bookings of the same
   * tourist are created one after the other (BR6). Must be called inside a transaction. Lock the
   * account before any vehicle or hub, so two operations never wait for each other.
   */
  Optional<User> findByIdForUpdate(UUID id);
}
