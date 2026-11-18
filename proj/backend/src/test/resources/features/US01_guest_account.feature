@KAN-53
Feature: Guest account and login (US01)
  As a tourist
  I want to create a guest account or log in if I already have one
  So that I can start using the service quickly and access my own data securely

  @BR10
  Scenario: A tourist creates a guest account with valid data
    When a tourist registers with name "Ana Sousa", email "ana.sousa@example.com" and password "Passw0rd!"
    Then the registration is accepted
    And the response contains an access token
    And an account with email "ana.sousa@example.com" exists with role "END_USER"

  Scenario Outline: Registration with invalid data is refused
    When a tourist registers with name "<name>", email "<email>" and password "<password>"
    Then the registration is refused as invalid
    And no account with email "<email>" exists

    Examples: Missing name
      | name | email                 | password  |
      |      | ana.sousa@example.com | Passw0rd! |

    Examples: Invalid email
      | name      | email                 | password  |
      | Ana Sousa |                       | Passw0rd! |
      | Ana Sousa | ana.sousa             | Passw0rd! |
      | Ana Sousa | ana.sousa@            | Passw0rd! |
      | Ana Sousa | @example.com          | Passw0rd! |
      | Ana Sousa | ana sousa@example.com | Passw0rd! |
      | Ana Sousa | ana.sousa@localhost   | Passw0rd! |

    Examples: Password too short
      | name      | email                 | password |
      | Ana Sousa | ana.sousa@example.com | Pass12!  |

  Scenario: Registration with an email that is already in use is refused
    Given a guest account exists with name "Ana Sousa", email "ana.sousa@example.com" and password "Passw0rd!"
    When a tourist registers with name "Ana S.", email "ANA.SOUSA@example.com" and password "Other123!"
    Then the registration is refused because the email is already in use

  Scenario: A registered tourist logs in with valid credentials
    Given a guest account exists with name "Ana Sousa", email "ana.sousa@example.com" and password "Passw0rd!"
    When a tourist logs in with email "ana.sousa@example.com" and password "Passw0rd!"
    Then the login is accepted
    And the response contains an access token

  Scenario Outline: Login with invalid credentials is refused
    Given a guest account exists with name "Ana Sousa", email "ana.sousa@example.com" and password "Passw0rd!"
    When a tourist logs in with email "<email>" and password "<password>"
    Then the login is refused as unauthorized

    Examples:
      | email                  | password    |
      | ana.sousa@example.com  | WrongPass1! |
      | bruno.lima@example.com | Passw0rd!   |

  @BR23
  Scenario: A tourist reads their own data
    Given a guest account exists with name "Ana Sousa", email "ana.sousa@example.com" and password "Passw0rd!"
    And the tourist is logged in with email "ana.sousa@example.com" and password "Passw0rd!"
    When the tourist requests the data of the account with email "ana.sousa@example.com"
    Then access is granted
    And the data shows name "Ana Sousa" and email "ana.sousa@example.com"

  @BR23
  Scenario: A tourist cannot read another tourist's data
    Given guest accounts exist:
      | name       | email                  | password  |
      | Ana Sousa  | ana.sousa@example.com  | Passw0rd! |
      | Bruno Lima | bruno.lima@example.com | Secr3t!xy |
    And the tourist is logged in with email "ana.sousa@example.com" and password "Passw0rd!"
    When the tourist requests the data of the account with email "bruno.lima@example.com"
    Then access is denied