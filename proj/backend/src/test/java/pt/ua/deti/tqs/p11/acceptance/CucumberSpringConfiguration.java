package pt.ua.deti.tqs.p11.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;

/** Starts the whole application on a random port with a real PostgreSQL for the scenarios. */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public class CucumberSpringConfiguration {}
