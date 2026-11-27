@REQ_KAN-63
Feature: Field technicians see their tickets and close them (US11)
  As a field technician
  I want to see my tickets ordered by SLA deadline and close them recording the work done
  So that I can prioritize my work and the vehicle returns to service

  # Tickets come from fault readings published on the real MQTT broker, as in US10, and are
  # assigned by the operator. A vehicle given "in state" X is registered at hub "hub-03".
  # "Opened N hours ago" moves the opening time and the deadline back in the database, so a scenario
  # does not wait for the clock. The exact limits of BR19 (24 h 00 min on time, 24 h 01 min
  # breached) are tested with a fixed clock in MaintenanceTicketTest; here, a ticket clearly on time
  # and one clearly late.

  Background:
    Given an operator is logged in
    And a vehicle "bike-0009" in state "Available"
    And a vehicle "bike-0010" in state "Available"
    And field technician "tiago.lopes@example.com" is logged in
    And field technician "ana.matos@example.com" is logged in

  # ---------------------------------------------------------------- The technician's list

  @BR19
  Scenario: A technician sees only their tickets, nearest deadline first, with where to find the vehicle
    Given the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the simulator has reported fault "FLAT_TIRE" for "bike-0010" at "2026-11-10T10:00:00Z"
    And the simulator has reported fault "BATTERY_OVERHEAT" for "bike-0010" at "2026-11-10T10:00:10Z"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is assigned to "tiago.lopes@example.com"
    And the ticket for fault "FLAT_TIRE" of vehicle "bike-0010" is assigned to "tiago.lopes@example.com"
    And the ticket for fault "BATTERY_OVERHEAT" of vehicle "bike-0010" is assigned to "ana.matos@example.com"
    And the ticket for fault "FLAT_TIRE" of vehicle "bike-0010" was opened 2 hours ago
    When "tiago.lopes@example.com" looks at their tickets
    Then they see these tickets, in order:
      | vehicle   | fault      | location | breached |
      | bike-0010 | FLAT_TIRE  | hub-03   | no       |
      | bike-0009 | BRAKE_WEAR | hub-03   | no       |

  @BR19
  Scenario: A ticket past its deadline is highlighted
    Given the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the simulator has reported fault "FLAT_TIRE" for "bike-0010" at "2026-11-10T10:00:00Z"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is assigned to "tiago.lopes@example.com"
    And the ticket for fault "FLAT_TIRE" of vehicle "bike-0010" is assigned to "tiago.lopes@example.com"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" was opened 25 hours ago
    When "tiago.lopes@example.com" looks at their tickets
    Then they see these tickets, in order:
      | vehicle   | fault      | location | breached |
      | bike-0009 | BRAKE_WEAR | hub-03   | yes      |
      | bike-0010 | FLAT_TIRE  | hub-03   | no       |

  # ---------------------------------------------------------------- Starting and closing

  Scenario: The technician starts the work on an assigned ticket
    Given the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is assigned to "tiago.lopes@example.com"
    When "tiago.lopes@example.com" starts the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009"
    Then the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is "InProgress"

  @BR20
  Scenario Outline: An InProgress ticket is closed only with a record of the work done
    Given the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is assigned to "tiago.lopes@example.com"
    And "tiago.lopes@example.com" has started the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009"
    When "tiago.lopes@example.com" closes the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" recording "<work done>"
    Then the closing is <outcome>
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is "<state>"

    Examples:
      | work done           | outcome                                | state      |
      | Replaced brake pads | accepted                               | Closed     |
      |                     | refused because the work done is empty | InProgress |

  @BR20
  Scenario: A ticket whose work has not started cannot be closed
    Given the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is assigned to "tiago.lopes@example.com"
    When "tiago.lopes@example.com" closes the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" recording "Replaced brake pads"
    Then the closing is refused because the work has not started
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is "Assigned"

  @BR20
  Scenario: Only the assigned technician can start or close the ticket
    Given the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is assigned to "tiago.lopes@example.com"
    When "ana.matos@example.com" starts the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009"
    Then starting the ticket is refused because it is not theirs
    Given "tiago.lopes@example.com" has started the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009"
    When "ana.matos@example.com" closes the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" recording "Replaced brake pads"
    Then the closing is refused because the ticket is not theirs
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is "InProgress"

  # ---------------------------------------------------------------- Back to service

  @BR20
  Scenario: A vehicle with no other open ticket returns to service
    Given the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is assigned to "tiago.lopes@example.com"
    And "tiago.lopes@example.com" has started the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009"
    And vehicle "bike-0009" is "Maintenance"
    When "tiago.lopes@example.com" closes the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" recording "Replaced brake pads"
    Then vehicle "bike-0009" is "Available"

  @BR20
  Scenario: A vehicle with another open ticket stays in maintenance
    Given the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the simulator has reported fault "FLAT_TIRE" for "bike-0009" at "2026-11-10T10:00:10Z"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is assigned to "tiago.lopes@example.com"
    And "tiago.lopes@example.com" has started the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009"
    When "tiago.lopes@example.com" closes the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" recording "Replaced brake pads"
    Then vehicle "bike-0009" is "Maintenance"
    And vehicle "bike-0009" has these open tickets:
      | fault     | state |
      | FLAT_TIRE | Open  |

  # ---------------------------------------------------------------- SLA and time to repair

  @BR19
  Scenario Outline: Closing records the time to repair and whether the SLA deadline was met
    Given the simulator has reported fault "BRAKE_WEAR" for "bike-0009" at "2026-11-10T10:00:00Z"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" is assigned to "tiago.lopes@example.com"
    And "tiago.lopes@example.com" has started the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009"
    And the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" was opened <hours> hours ago
    When "tiago.lopes@example.com" closes the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" recording "Replaced brake pads"
    Then the ticket for fault "BRAKE_WEAR" of vehicle "bike-0009" was closed <on time>
    And its time to repair is about <hours> hours

    Examples:
      | hours | on time             |
      | 23    | on time             |
      | 25    | after its deadline  |
