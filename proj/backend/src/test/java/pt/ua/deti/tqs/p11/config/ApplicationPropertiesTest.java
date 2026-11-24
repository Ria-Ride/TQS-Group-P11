package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * application.properties has no inline comments: a comment glued to the end of a value becomes part
 * of it. It happened once, when a line was appended to a file with no final newline: the admin
 * password default grew a comment and the application refused to start.
 */
class ApplicationPropertiesTest {

  private static Properties load() throws IOException {
    Properties properties = new Properties();
    try (InputStream in =
        ApplicationPropertiesTest.class.getResourceAsStream("/application.properties")) {
      properties.load(in);
    }
    return properties;
  }

  @Test
  void adminSettings_areExactlyTheirEnvironmentVariables() throws IOException {
    Properties properties = load();

    assertThat(properties.getProperty("app.admin.email")).isEqualTo("${ADMIN_EMAIL:}");
    assertThat(properties.getProperty("app.admin.password")).isEqualTo("${ADMIN_PASSWORD:}");
  }

  @Test
  void noValueContainsAComment() throws IOException {
    Properties properties = load();

    assertThat(properties.stringPropertyNames())
        .allSatisfy(name -> assertThat(properties.getProperty(name)).doesNotContain("#"));
  }
}
