package pt.ua.deti.tqs.p11.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.User;

@ExtendWith(MockitoExtension.class)
class GetUserProfileUseCaseTest {

  private static final UUID ANA_ID = UUID.fromString("4d5e6f70-8192-43a4-b5c6-d7e8f90a1b2c");

  @Mock private UserRepository users;

  @Test
  void get_existingUser_returnsIt() {
    User ana =
        User.guest(ANA_ID, "Ana Sousa", new EmailAddress("ana.sousa@example.com"), "ana-hash");
    when(users.findById(ANA_ID)).thenReturn(Optional.of(ana));

    assertThat(new GetUserProfileUseCase(users).get(ANA_ID)).isEqualTo(ana);
  }

  @Test
  void get_unknownUser_throwsNotFound() {
    when(users.findById(ANA_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new GetUserProfileUseCase(users).get(ANA_ID))
        .isInstanceOf(UserNotFoundException.class);
  }
}