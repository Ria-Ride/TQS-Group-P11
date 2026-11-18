package pt.ua.deti.tqs.p11.application.user;

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
}
