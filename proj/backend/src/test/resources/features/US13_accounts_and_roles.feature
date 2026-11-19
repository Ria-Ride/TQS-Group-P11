@KAN-65
Feature: User accounts and roles (US13)
  As a platform admin
  I want to manage user accounts and roles, with role-based access enforced in the API
  So that each person only has the permissions they need

  Background:
    Given an admin is logged in

  Scenario Outline: The admin creates an operator account with or without the technician flag
    When the admin creates an account for "Rui Costa" with email "rui.costa@example.com", role "OPERATOR" and field technician <technician>
    Then the account is created
    And the account with email "rui.costa@example.com" has role "OPERATOR" and field technician <technician>

    Examples:
      | technician |
      | true       |
      | false      |

  @BR20
  Scenario: Only an operator can be a field technician
    When the admin creates an account for "Rui Costa" with email "rui.costa@example.com", role "CLIENT_MANAGER" and field technician true
    Then the response status is 400

  Scenario: The admin changes the role of an account
    Given the admin has created an operator account with email "rui.costa@example.com" and password "Passw0rd!"
    When the admin changes the role of the account with email "rui.costa@example.com" to "CLIENT_MANAGER"
    Then the account with email "rui.costa@example.com" has role "CLIENT_MANAGER" and field technician false

  Scenario: A deactivated account can no longer log in
    Given the admin has created an operator account with email "rui.costa@example.com" and password "Passw0rd!"
    When the admin deactivates the account with email "rui.costa@example.com"
    Then logging in with email "rui.costa@example.com" and password "Passw0rd!" is refused

  # The full role x endpoint matrix is checked by AccessMatrixApiIT; these rows illustrate it.
  @BR23
  Scenario Outline: Each role only reaches what the access matrix allows
    Given a user with role "<role>" is logged in
    When they request <method> "<path>"
    Then the response status is <status>

    Examples:
      | role           | method | path            | status |
      | END_USER       | GET    | /admin/users    | 403    |
      | OPERATOR       | GET    | /admin/users    | 403    |
      | CLIENT_MANAGER | GET    | /admin/users    | 403    |
      | ADMIN          | GET    | /admin/users    | 200    |
      | END_USER       | POST   | /hubs           | 403    |
      | CLIENT_MANAGER | GET    | /fleet/overview | 403    |

  @BR23
  Scenario: A request without a token is refused with 401
    When an anonymous visitor requests GET "/admin/users"
    Then the response status is 401

  @BR23
  Scenario: A request with an invalid token is refused with 401
    When someone requests GET "/admin/users" with an invalid token
    Then the response status is 401
