package pt.ua.deti.tqs.p11.adapter.out.persistence.user;

import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.auth.EmailAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.User;

/** UserRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class UserPersistenceAdapter implements UserRepository {

  /** Name of the unique constraint in V1__create_users.sql. */
  static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

  private final SpringDataUserRepository repository;

  UserPersistenceAdapter(SpringDataUserRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean existsByEmail(EmailAddress email) {
    return repository.existsByEmail(email.value());
  }

  @Override
  public User save(User user) {
    try {
      // Flush now, so a unique violation surfaces here and not at some later commit.
      return repository.saveAndFlush(UserJpaEntity.from(user)).toDomain();
    } catch (DataIntegrityViolationException e) {
      if (isEmailConflict(e)) {
        throw new EmailAlreadyInUseException();
      }
      throw e;
    }
  }

  private static boolean isEmailConflict(DataIntegrityViolationException e) {
    String cause = e.getMostSpecificCause().getMessage();
    return cause != null && cause.contains(EMAIL_UNIQUE_CONSTRAINT);
  }

  @Override
  public Optional<User> findByEmail(EmailAddress email) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
