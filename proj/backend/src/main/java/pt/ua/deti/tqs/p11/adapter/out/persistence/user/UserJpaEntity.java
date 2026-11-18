package pt.ua.deti.tqs.p11.adapter.out.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/** JPA mapping of the users table. Kept out of the domain (no jakarta there). */
@Entity
@Table(name = "users")
class UserJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  /** For JPA only. */
  protected UserJpaEntity() {}

  static UserJpaEntity from(User user) {
    UserJpaEntity entity = new UserJpaEntity();
    entity.id = user.id();
    entity.name = user.name();
    entity.email = user.email().value();
    entity.passwordHash = user.passwordHash();
    entity.role = user.role();
    return entity;
  }

  User toDomain() {
    return new User(id, name, new EmailAddress(email), passwordHash, role);
  }
}
