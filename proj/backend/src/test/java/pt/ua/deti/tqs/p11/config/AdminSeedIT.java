package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.ApiClient;

/**
 * US13 (KAN-77): the initial ADMIN comes from configuration (ADMIN_EMAIL / ADMIN_PASSWORD in real
 * environments), never from a credential in the repository. These values are test-only.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin.email=root.admin@example.com", "app.admin.password=Adm1nPass!"})
@Import(TestcontainersConfiguration.class)
class AdminSeedIT {

  @Value("${local.server.port}")
  private int port;

  @Test
  void initialAdmin_canLogInAndManageAccounts() {
    RestClient http = ApiClient.forPort(port);

    ResponseEntity<Map<String, Object>> login =
        http.post()
            .uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", "root.admin@example.com", "password", "Adm1nPass!"))
            .retrieve()
            .toEntity(ApiClient.JSON_OBJECT);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

    String token = (String) login.getBody().get("accessToken");
    assertThat(
            http.get()
                .uri("/admin/users")
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity()
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }
}
