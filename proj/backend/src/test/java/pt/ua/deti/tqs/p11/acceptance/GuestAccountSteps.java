package pt.ua.deti.tqs.p11.acceptance;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assumptions;

/** Steps for US01 (KAN-53). Pending until the endpoints exist; implemented in a later step. */
public class GuestAccountSteps {

  @Given("a guest account exists with name {string}, email {string} and password {string}")
  public void aGuestAccountExists(String name, String email, String password) {
    pending();
  }

  @Given("guest accounts exist:")
  public void guestAccountsExist(DataTable accounts) {
    pending();
  }

  @When("a tourist registers with name {string}, email {string} and password {string}")
  public void aTouristRegisters(String name, String email, String password) {
    pending();
  }

  @Then("the registration is accepted")
  public void theRegistrationIsAccepted() {
    pending();
  }

  @Then("the registration is refused as invalid")
  public void theRegistrationIsRefusedAsInvalid() {
    pending();
  }

  @Then("the registration is refused because the email is already in use")
  public void theRegistrationIsRefusedBecauseTheEmailIsInUse() {
    pending();
  }

  @Then("the response contains an access token")
  public void theResponseContainsAnAccessToken() {
    pending();
  }

  @Then("an account with email {string} exists with role {string}")
  public void anAccountExistsWithRole(String email, String role) {
    pending();
  }

  @Then("no account with email {string} exists")
  public void noAccountExists(String email) {
    pending();
  }

  @When("a tourist logs in with email {string} and password {string}")
  public void aTouristLogsIn(String email, String password) {
    pending();
  }

  @Given("the tourist is logged in with email {string} and password {string}")
  public void theTouristIsLoggedIn(String email, String password) {
    pending();
  }

  @Then("the login is accepted")
  public void theLoginIsAccepted() {
    pending();
  }

  @Then("the login is refused as unauthorized")
  public void theLoginIsRefusedAsUnauthorized() {
    pending();
  }

  @When("the tourist requests the data of the account with email {string}")
  public void theTouristRequestsTheDataOf(String email) {
    pending();
  }

  @Then("access is granted")
  public void accessIsGranted() {
    pending();
  }

  @Then("access is denied")
  public void accessIsDenied() {
    pending();
  }

  @Then("the data shows name {string} and email {string}")
  public void theDataShows(String name, String email) {
    pending();
  }

  /** Marks a step as not implemented yet: the scenario is reported as skipped, not failed. */
  private static void pending() {
    Assumptions.abort("Pending: step not implemented yet (KAN-53)");
  }
}
