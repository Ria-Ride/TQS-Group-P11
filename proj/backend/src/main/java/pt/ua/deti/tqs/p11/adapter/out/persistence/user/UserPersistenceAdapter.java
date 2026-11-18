package pt.ua.deti.tqs.p11.adapter.out.persistence.user;

import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.User;

/** UserRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class UserPersistenceAdapter implements UserRepository {

  @Override
  public boolean existsByEmail(EmailAddress email) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public User save(User user) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}