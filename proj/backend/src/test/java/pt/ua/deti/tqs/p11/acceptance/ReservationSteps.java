package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.acceptance.BookingSteps.Refusal;
import pt.ua.deti.tqs.p11.application.booking.ManageBookingUseCase;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.FleetApi;
import pt.ua.deti.tqs.p11.support.ShiftableClock;
import pt.ua.deti.tqs.p11.support.SimulatedVehicles;

/**
 * Steps for US05 (KAN-57) and US06 (KAN-58), run against the real API, database, MQTT broker and
 * simulated payment gateway. The platform clock is fixed at "now" by the Background and moved only
 * by the steps, so the start window (BR7) is checked at its exact boundaries. Times in the
 * scenarios are minutes from that "now". Tourists, passes and the deposit and unlock checks are
 * shared with BookingSteps.
 */
public class ReservationSteps {

  private static final Map<String, String> CARDS =
      Map.of("approved", "tok_approve", "declined", "tok_decline", "timed out", "tok_timeout");

  /** "1 minute before", "0 minutes", "15 minutes 1 second". */
  private static final Pattern OFFSET =
      Pattern.compile("(\\d+) minutes?(?: (\\d+) seconds?)?( before)?");

  private static final String BOOKING = "booking";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ShiftableClock clock;
  @Autowired private BookingSteps bookingSteps;
  @Autowired private ManageBookingUseCase expiry;
  @Autowired private VehicleSearchSteps searchSteps;

  /** US06: the vehicles on the broker, answering lock commands; shared by all scenarios. */
  private static SimulatedVehicles vehicles;

  private RestClient http;
  private Instant now;
  private ResponseEntity<Map<String, Object>> lastBooking;
  private ResponseEntity<Map<String, Object>> lastAction;
  private UUID bookingId;
  private Instant plannedStart;

  /** Every scenario ends with the real time back, so the scenarios of other stories see it. */
  @After
  public void releaseTheClock() {
    clock.release();
  }

  // ------------------------------------------------------------- helpers

  /** The booking of the scenario: one booked here (US05), or a ride started by BookingSteps. */
  private UUID currentBooking() {
    if (bookingId != null) {
      return bookingId;
    }
    Map<String, Object> started = bookingSteps.lastBooking();
    if (started == null) {
      throw new IllegalStateException("No booking in this scenario");
    }
    return UUID.fromString((String) started.get("id"));
  }

  /**
   * A token issued now, by logging in again: the platform clock may have moved past the hour a
   * token lasts (the token service reads the same clock).
   */
  private String freshToken(String name) {
    Map<String, Object> login =
        http.post()
            .uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", name.toLowerCase() + "@example.com", "password", "Passw0rd!"))
            .retrieve()
            .toEntity(ApiClient.JSON_OBJECT)
            .getBody();
    return (String) login.get("accessToken");
  }

  private ResponseEntity<Map<String, Object>> book(
      String name, String vehicle, int fromMinute, int toMinute, String card) {
    return http.post()
        .uri("/bookings")
        .header("Authorization", "Bearer " + bookingSteps.tourist(name).token())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "vehicleId", bookingSteps.idOf("vehicles", vehicle),
                "plannedStart", minute(fromMinute).toString(),
                "plannedEnd", minute(toMinute).toString(),
                "paymentToken", card))
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private void remember(ResponseEntity<Map<String, Object>> response) {
    lastBooking = response;
    bookingSteps.remember(response);
    if (response.getStatusCode() == HttpStatus.CREATED) {
      bookingId = UUID.fromString((String) response.getBody().get("id"));
      plannedStart = Instant.parse((String) response.getBody().get("plannedStart"));
    }
  }

  private ResponseEntity<Map<String, Object>> act(String name, String action) {
    return http.post()
        .uri("/bookings/{id}/{action}", currentBooking(), action)
        .header("Authorization", "Bearer " + freshToken(name))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of())
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private Instant minute(int minutes) {
    return now.plus(Duration.ofMinutes(minutes));
  }

  private String bookingState() {
    return jdbc.queryForObject(
        "SELECT state FROM bookings WHERE id = ?", String.class, currentBooking());
  }

  private List<String> depositStates() {
    return jdbc.queryForList(
        "SELECT state FROM payments WHERE booking_id = ? AND kind = 'DEPOSIT'",
        String.class,
        currentBooking());
  }

  /** The refusals of US05 not already in US04, as ApiExceptionHandler sends them. */
  private static Refusal refusal(String reason) {
    return switch (reason) {
      case "it is too early" ->
          new Refusal(HttpStatus.CONFLICT, BOOKING, "cannot be started before its planned start");
      case "the start window has passed" ->
          new Refusal(HttpStatus.CONFLICT, BOOKING, "cannot be started after its start window");
      case "the booking is not Reserved" ->
          new Refusal(HttpStatus.CONFLICT, BOOKING, "is not Reserved");
      default -> BookingSteps.refusal(reason);
    };
  }

  private static void assertRefused(ResponseEntity<Map<String, Object>> response, String reason) {
    Refusal expected = refusal(reason);
    assertThat(response.getStatusCode()).isEqualTo(expected.status());
    assertThat(FleetApi.errors(response)).containsEntry(expected.field(), expected.message());
  }

  // ------------------------------------------------------------- Given

  @Given("the platform clock is fixed")
  public void thePlatformClockIsFixed() {
    http = ApiClient.forPort(port);
    now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    clock.fixAt(now);
    bookingSteps.useNow(now);
  }

  @Given("{string} has a {string} pass valid from minute {int} to minute {int}")
  public void hasPassValidBetween(String name, String passType, int from, int to) {
    bookingSteps.insertPass(
        bookingSteps.tourist(name).id(),
        bookingSteps.idOf("pass_types", passType),
        minute(from),
        minute(to));
  }

  @Given("{string} has booked {string} from minute {int} to minute {int}")
  public void hasBooked(String name, String vehicle, int from, int to) {
    ResponseEntity<Map<String, Object>> booked = book(name, vehicle, from, to, "tok_approve");
    assertThat(booked.getStatusCode())
        .as("setup: %s books %s", name, vehicle)
        .isEqualTo(HttpStatus.CREATED);
    remember(booked);
  }

  @Given("{string} has started the booking")
  public void hasStartedTheBooking(String name) {
    assertThat(act(name, "start").getStatusCode())
        .as("setup: %s starts the booking", name)
        .isEqualTo(HttpStatus.OK);
  }

  /** Moves the fixed clock relative to the planned start of the current booking. */
  @Given("it is {} after the planned start of the booking")
  public void itIsAfterThePlannedStart(String offset) {
    Matcher parts = OFFSET.matcher(offset);
    assertThat(parts.matches()).as("offset %s", offset).isTrue();
    Duration shift =
        Duration.ofMinutes(Long.parseLong(parts.group(1)))
            .plusSeconds(parts.group(2) == null ? 0 : Long.parseLong(parts.group(2)));
    clock.fixAt(parts.group(3) == null ? plannedStart.plus(shift) : plannedStart.minus(shift));
  }

  // ------------------------------------------------------------- When

  @When("{string} books {string} from minute {int} to minute {int}")
  public void books(String name, String vehicle, int from, int to) {
    remember(book(name, vehicle, from, to, "tok_approve"));
  }

  @When("{string} books {string} from minute {int} to minute {int} and the deposit is {outcome}")
  public void booksWithDepositOutcome(
      String name, String vehicle, int from, int to, String outcome) {
    remember(book(name, vehicle, from, to, CARDS.get(outcome)));
  }

  @When("{string} starts the booking")
  public void startsTheBooking(String name) {
    lastAction = act(name, "start");
  }

  @When("{string} cancels the booking")
  public void cancelsTheBooking(String name) {
    lastAction = act(name, "cancel");
  }

  @When("the expiry of reservations runs")
  public void theExpiryRuns() {
    expiry.expireOverdue();
  }

  // ------------------------------------------------------------- Then

  @Then("{string} has a Reserved booking of {string} at hub {string}")
  public void hasReservedBooking(String name, String vehicle, String hub) {
    assertThat(lastBooking.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(lastBooking.getBody())
        .containsEntry("state", "RESERVED")
        .containsEntry("vehicleId", bookingSteps.idOf("vehicles", vehicle).toString())
        .containsEntry("startHubId", bookingSteps.idOf("hubs", hub).toString())
        .containsEntry("startedAt", null);
    assertThat(
            bookingSteps.count(
                "SELECT COUNT(*) FROM bookings WHERE account_id = ? AND vehicle_id = ?"
                    + " AND state = 'RESERVED'",
                bookingSteps.tourist(name).id(),
                bookingSteps.idOf("vehicles", vehicle)))
        .isEqualTo(1);
  }

  @Then("the booking is accepted")
  public void theBookingIsAccepted() {
    assertThat(lastBooking.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Then("the booking is refused because {}")
  public void theBookingIsRefused(String reason) {
    assertRefused(lastBooking, reason);
  }

  @Then("the start of the booking is accepted")
  public void theStartIsAccepted() {
    assertThat(lastAction.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(lastAction.getBody()).containsEntry("state", "ACTIVE");
  }

  @Then("the start of the booking is refused because {}")
  public void theStartIsRefused(String reason) {
    assertRefused(lastAction, reason);
  }

  @Then("the cancellation is refused because {}")
  public void theCancellationIsRefused(String reason) {
    assertRefused(lastAction, reason);
  }

  @Then("access to the booking is denied")
  public void accessIsDenied() {
    assertThat(lastAction.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Then("the booking is {string}")
  public void theBookingIs(String state) {
    assertThat(bookingState()).isEqualTo(state);
  }

  /** BR18: the fault arrives by MQTT, so the cancellation is awaited. */
  @Then("the booking is {string} with reason {string}")
  public void theBookingIsWithReason(String state, String reason) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(bookingState()).isEqualTo(state));
    Map<String, Object> row =
        new HashMap<>(
            jdbc.queryForMap("SELECT cancel_reason FROM bookings WHERE id = ?", currentBooking()));
    assertThat(row).containsEntry("cancel_reason", reason);
  }

  @Then("the deposit of the booking is still authorized")
  public void theDepositIsStillAuthorized() {
    assertThat(depositStates()).containsExactly("AUTHORIZED");
  }

  @Then("the deposit of the booking is released")
  public void theDepositIsReleased() {
    assertThat(depositStates()).containsExactly("RELEASED");
  }

  // ------------------------------------------------------------- US06: the end of a ride

  private static synchronized SimulatedVehicles vehicles() {
    if (vehicles == null) {
      vehicles =
          new SimulatedVehicles(
              CucumberSpringConfiguration.BROKER.host(), CucumberSpringConfiguration.BROKER.port());
    }
    return vehicles;
  }

  private ResponseEntity<Map<String, Object>> end(String name, String hub) {
    return http.post()
        .uri("/bookings/{id}/end", currentBooking())
        .header("Authorization", "Bearer " + freshToken(name))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("hubId", searchSteps.hubId(hub)))
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private Instant plannedEnd() {
    return jdbc.queryForObject(
            "SELECT planned_end FROM bookings WHERE id = ?", Timestamp.class, currentBooking())
        .toInstant();
  }

  @Given("the vehicles answer lock commands")
  public void theVehiclesAnswerLockCommands() {
    vehicles().reset();
  }

  @Given("vehicle {string} will answer the lock with REJECTED {string}")
  public void vehicleWillReject(String vehicle, String reason) {
    vehicles().willAnswer(vehicle, new SimulatedVehicles.Answer.Rejected(reason));
  }

  @Given("vehicle {string} will not answer the lock")
  public void vehicleWillNotAnswer(String vehicle) {
    vehicles().willAnswer(vehicle, new SimulatedVehicles.Answer.Silent());
  }

  /** Moves the fixed clock to that many minutes after the planned end of the ride. */
  @Given("it is {int} minutes late")
  public void itIsMinutesLate(int minutes) {
    clock.fixAt(plannedEnd().plus(Duration.ofMinutes(minutes)));
  }

  @Given("{string} has tried to end the ride at the full hub {string}")
  public void hasTriedAtAFullHub(String name, String hub) {
    assertThat(end(name, hub).getStatusCode())
        .as("setup: %s is refused at the full hub %s", name, hub)
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @When("{string} ends the ride at hub {string}")
  public void endsTheRide(String name, String hub) {
    lastAction = end(name, hub);
  }

  @Then("the ride has ended at hub {string}")
  public void theRideHasEnded(String hub) {
    assertThat(lastAction.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(lastAction.getBody())
        .containsEntry("state", "COMPLETED")
        .containsEntry("endHubId", searchSteps.hubId(hub).toString());
    assertThat(bookingState()).isEqualTo("COMPLETED");
  }

  @Then("vehicle {string} receives a lock command")
  public void receivesALockCommand(String vehicle) {
    assertThat(vehicles().lockedVehicles()).contains(vehicle);
  }

  @Then("the end is refused because the vehicle could not be locked")
  public void refusedNotLocked() {
    assertThat(lastAction.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(FleetApi.errors(lastAction))
        .containsEntry("vehicleId", "could not be locked; check that it is parked in the hub");
  }

  @Then("the end is refused because the vehicle did not answer in time")
  public void refusedNoAnswer() {
    assertThat(lastAction.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    assertThat(FleetApi.errors(lastAction))
        .containsEntry("vehicleId", "did not answer in time; try again");
  }

  @Then("the end is refused because the hub is full")
  public void refusedFullHub() {
    assertThat(lastAction.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(FleetApi.errors(lastAction)).containsEntry("hubId", "is full");
  }

  @Then("these hubs are suggested, nearest first: {string}")
  public void theseHubsAreSuggested(String codes) {
    List<Object> nearby =
        ((List<?>) lastAction.getBody().get("nearbyHubs"))
            .stream().map(hub -> (Object) ((Map<?, ?>) hub).get("code")).toList();
    assertThat(nearby).containsExactly((Object[]) codes.split(",\\s*"));
  }

  @Then("the first full-hub attempt is recorded {int} minutes late")
  public void theFirstAttemptIsRecorded(int minutes) {
    Timestamp at =
        jdbc.queryForObject(
            "SELECT full_hub_attempt_at FROM bookings WHERE id = ?",
            Timestamp.class,
            currentBooking());
    assertThat(at).isNotNull();
    assertThat(at.toInstant()).isEqualTo(plannedEnd().plus(Duration.ofMinutes(minutes)));
  }

  @Then("the overtime charge is {bigdecimal} EUR")
  public void theOvertimeChargeIs(BigDecimal charge) {
    assertThat(lastAction.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(lastAction.getBody())
        .containsEntry("overtimeCharge", charge.setScale(2).toPlainString());
  }

  /** BR13: as the API tells it, and as the deposit is kept. */
  @Then("{bigdecimal} EUR of the deposit is captured and {bigdecimal} EUR released")
  public void theDepositIsSettled(BigDecimal captured, BigDecimal released) {
    assertThat(lastAction.getBody())
        .containsEntry("depositCaptured", captured.setScale(2).toPlainString())
        .containsEntry("depositReleased", released.setScale(2).toPlainString());
    Map<String, Object> deposit =
        jdbc.queryForMap(
            "SELECT state, captured_amount FROM payments WHERE booking_id = ? AND kind = 'DEPOSIT'",
            currentBooking());
    if (captured.signum() == 0) {
      assertThat(deposit).containsEntry("state", "RELEASED").containsEntry("captured_amount", null);
    } else {
      assertThat(deposit).containsEntry("state", "CAPTURED");
      assertThat((BigDecimal) deposit.get("captured_amount")).isEqualByComparingTo(captured);
    }
  }
}
