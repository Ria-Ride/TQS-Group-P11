package pt.ua.deti.tqs.p11.acceptance;

import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;

/**
 * Steps for US04 (KAN-56), run against the real API, database, MQTT broker and simulated payment
 * gateway. Skeletons for now (KAN-697): each step is pending until the story is implemented.
 * Tourists log in, hubs and vehicles are set up and vehicle states are checked with the steps of
 * PassPurchaseSteps, VehicleSearchSteps and FleetSteps.
 */
public class BookingSteps {

  // Given

  @Given("{string} has a valid {string} pass")
  public void hasValidPass(String tourist, String passType) {
    throw new PendingException();
  }

  @Given("{string} has a valid pass that allows only {string}")
  public void hasValidPassAllowingOnly(String tourist, String vehicleType) {
    throw new PendingException();
  }

  @Given("{string} has a valid {string} pass that ends in {int} minutes")
  public void hasValidPassEndingIn(String tourist, String passType, int minutes) {
    throw new PendingException();
  }

  @Given("{string} has started using {string} for {int} minutes")
  public void hasStartedUsing(String tourist, String vehicle, int minutes) {
    throw new PendingException();
  }

  @Given("{int} tourists with a valid {string} pass")
  public void touristsWithValidPass(int count, String passType) {
    throw new PendingException();
  }

  // When

  @When("{string} starts using {string} for {int} minutes")
  public void startsUsing(String tourist, String vehicle, int minutes) {
    throw new PendingException();
  }

  @When("{string} starts using {string} for {int} minutes and the deposit is {outcome}")
  public void startsUsingWithDepositOutcome(
      String tourist, String vehicle, int minutes, String outcome) {
    throw new PendingException();
  }

  @When("they all start using {string} for {int} minutes at the same time")
  public void allStartAtTheSameTime(String vehicle, int minutes) {
    throw new PendingException();
  }

  // Then

  @Then("{string} has an Active booking of {string} starting at hub {string}")
  public void hasActiveBooking(String tourist, String vehicle, String hub) {
    throw new PendingException();
  }

  @Then("a deposit of {bigdecimal} EUR is authorized for the booking")
  public void depositAuthorized(BigDecimal amount) {
    throw new PendingException();
  }

  @Then("the state history of {string} records the change from {word} to {word}")
  public void historyRecordsChange(String vehicle, String from, String to) {
    throw new PendingException();
  }

  @Then("vehicle {string} receives an unlock command")
  public void receivesUnlockCommand(String vehicle) {
    throw new PendingException();
  }

  @Then("the start is accepted")
  public void startAccepted() {
    throw new PendingException();
  }

  /** Every refusal reason of the feature, mapped later to the HTTP status and error field. */
  @Then("the start is refused because {}")
  public void startRefused(String reason) {
    throw new PendingException();
  }

  @Then("no deposit is authorized for {string}")
  public void noDepositAuthorized(String tourist) {
    throw new PendingException();
  }

  @Then("{string} has no booking")
  public void hasNoBooking(String tourist) {
    throw new PendingException();
  }

  @Then("exactly one of them has an Active booking of {string}")
  public void exactlyOneHasActiveBooking(String vehicle) {
    throw new PendingException();
  }

  @Then("the other {int} are refused because the vehicle is not available")
  public void othersRefused(int losers) {
    throw new PendingException();
  }

  @Then("the deposit of every refused tourist is released")
  public void depositsOfLosersReleased() {
    throw new PendingException();
  }
}