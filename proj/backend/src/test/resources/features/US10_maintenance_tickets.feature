@REQ_KAN-62
Feature: Maintenance tickets opened from faults or manually, and assigned (US10)
  As a service operator
  I want maintenance tickets opened automatically from faults or manually, and assigned to a technician
  So that faults are handled before the SLA deadline is at risk

  # Fault readings are published on a real MQTT broker, as the simulator does (contract 1.0.0).
  # The simulator repeats the current fault code in every reading while the fault lasts, so the
  # same fault arrives many times: BR20 keeps one open ticket per vehicle and fault code.
  # A vehicle given "in state" X is registered at hub "hub-03" and brought to X, as in US08.
  # The cancellation of the Booking of a Reserved vehicle with a fault, and its deposit, is shown
  # in US05_future_booking.feature; here, what the fault does to the vehicle. The end of a ride with
  # an open ticket is in US06_end_use.feature.
  # Closing tickets, breached tickets and the return to Available belong to US11.

  Background:
    Given an operator is logged in

  # ---------------------------------------------------------------- Tickets opened from faults

  @BR18 @BR19
  Scenario: A fault reading opens a ticket and sends an Available vehicle to maintenance
    Given a vehicle "bike-0009" in state "Available"
    When the simulator reports fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    Then vehicle "bike-0009" has these open tickets:
      | fault      | state |
      | BRAKE_WEAR | Open  |
    And the SLA deadline of the ticket for fault "BRAKE_WEAR" is 24 hours after it was opened
    And vehicle "bike-0009" is "Maintenance"

  @BR20
  Scenario: Repeated readings of the same fault open only one ticket
    Given a vehicle "bike-0009" in state "Available"
    When the simulator reports fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the simulator reports fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:10Z"
    Then vehicle "bike-0009" has these open tickets:
      | fault      | state |
      | BRAKE_WEAR | Open  |

  @BR20
  Scenario: A different fault on the same vehicle opens a second ticket
    Given a vehicle "bike-0009" in state "Available"
    When the simulator reports fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the simulator reports fault "LOW_TYRE_PRESSURE" for "bike-0009" at "2026-11-10T10:00:10Z"
    Then vehicle "bike-0009" has these open tickets:
      | fault             | state |
      | BRAKE_WEAR        | Open  |
      | LOW_TYRE_PRESSURE | Open  |
    And vehicle "bike-0009" is "Maintenance"

  @BR18
  Scenario: A fault on a vehicle in use does not interrupt the ride
    Given a vehicle "bike-0009" in state "InUse"
    When the simulator reports fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    Then vehicle "bike-0009" has these open tickets:
      | fault      | state |
      | BRAKE_WEAR | Open  |
    And vehicle "bike-0009" is "InUse" and belongs to no hub

  @BR18
  Scenario: A fault on a Reserved vehicle sends it to maintenance
    Given a vehicle "bike-0009" in state "Reserved"
    When the simulator reports fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    Then vehicle "bike-0009" has these open tickets:
      | fault      | state |
      | BRAKE_WEAR | Open  |
    And vehicle "bike-0009" is "Maintenance"

  @BR16 @BR18
  Scenario: A Deactivated vehicle with an open ticket cannot be activated
    Given a vehicle "bike-0009" in state "Deactivated"
    When the simulator reports fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    Then vehicle "bike-0009" has these open tickets:
      | fault      | state |
      | BRAKE_WEAR | Open  |
    And vehicle "bike-0009" is "Deactivated"
    When the operator activates vehicle "bike-0009"
    Then the transition is refused because the vehicle has an open ticket
    And vehicle "bike-0009" is "Deactivated"

  # ---------------------------------------------------------------- Manual tickets

  @BR18 @BR19
  Scenario: The operator opens a ticket manually for a fault the simulator cannot detect
    Given a vehicle "bike-0009" in state "Available"
    When the operator opens a ticket for vehicle "bike-0009" with fault "BROKEN_BASKET" and description "Front basket cracked"
    Then the ticket is opened
    And vehicle "bike-0009" has these open tickets:
      | fault         | state |
      | BROKEN_BASKET | Open  |
    And the SLA deadline of the ticket for fault "BROKEN_BASKET" is 24 hours after it was opened
    And vehicle "bike-0009" is "Maintenance"

  @BR20
  Scenario: A manual ticket for a fault already covered is refused
    Given a vehicle "bike-0009" in state "Available"
    And the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    When the operator opens a ticket for vehicle "bike-0009" with fault "BRAKE_WEAR" and description "Brakes squeak"
    Then the ticket is refused because the fault is already covered by an open ticket
    And vehicle "bike-0009" has these open tickets:
      | fault      | state |
      | BRAKE_WEAR | Open  |

  # ---------------------------------------------------------------- Assignment

  @BR20
  Scenario Outline: Only an operator who is a field technician can be assigned
    Given a vehicle "bike-0009" in state "Available"
    And the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And an account "<email>" with role "<role>" and field technician <technician>
    When the operator assigns the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" to "<email>"
    Then the assignment is <outcome>
    And vehicle "bike-0009" has these open tickets:
      | fault      | state   |
      | BRAKE_WEAR | <state> |

    Examples:
      | email                | role           | technician | outcome  | state    |
      | tiago@example.com    | OPERATOR       | true       | accepted | Assigned |
      | rita@example.com     | OPERATOR       | false      | refused  | Open     |
      | manuel@example.com   | CLIENT_MANAGER | false      | refused  | Open     |
      | joana@example.com    | END_USER       | false      | refused  | Open     |
