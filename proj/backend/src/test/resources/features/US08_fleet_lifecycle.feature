@KAN-60
Feature: Hubs, vehicles and vehicle lifecycle (US08)
  As a service operator
  I want to register hubs and vehicles and manage the vehicle lifecycle (activate, deactivate, relocate, retire)
  So that the fleet is organized and only vehicles in good condition are offered

  # Hubs and vehicles are identified by a code, as in the telemetry simulator (hub-03, bike-0009).
  # A vehicle given "in a state" is placed at hub "hub-03" (capacity 10), unless the state belongs
  # to no hub (InUse, InTransit, Retired).
  # Bookings (US04, US05) and maintenance tickets (US10) do not exist yet, so the steps put a
  # vehicle in Reserved, InUse or Maintenance directly in the database. Activation refused because
  # of an open ticket is added with US10.
  # The full state x action table of BR16 is covered by a parameterised unit test; the scenarios
  # below illustrate it, as US13 does for the access matrix.

  Background:
    Given an operator is logged in

  # ---------------------------------------------------------------- Hubs

  Scenario: The operator registers a hub
    When the operator registers hub "hub-03" named "Canal Central" at 40.6405, -8.6538 with capacity 10
    Then hub "hub-03" exists with capacity 10 and holds 0 vehicles

  Scenario Outline: A hub with invalid data is refused
    When the operator registers hub "hub-03" named "<name>" at <latitude>, <longitude> with capacity <capacity>
    Then the hub is refused as invalid
    And hub "hub-03" does not exist

    Examples: Missing name
      | name | latitude | longitude | capacity |
      |      | 40.6405  | -8.6538   | 10       |

    Examples: Capacity below 1
      | name          | latitude | longitude | capacity |
      | Canal Central | 40.6405  | -8.6538   | 0        |

    Examples: Position outside the globe
      | name          | latitude | longitude | capacity |
      | Canal Central | 90.0001  | -8.6538   | 10       |
      | Canal Central | 40.6405  | -180.0001 | 10       |

  Scenario: A hub code can only be used once
    Given hub "hub-03" with capacity 10 exists
    When the operator registers hub "hub-03" named "Fórum Aveiro" at 40.6412, -8.6501 with capacity 5
    Then the hub is refused because the code is already in use

  @BR14
  Scenario Outline: Capacity cannot be lowered below the vehicles held
    Given hub "hub-03" with capacity 10 holds <held> vehicles
    When the operator changes the capacity of hub "hub-03" to 9
    Then the capacity change is <outcome>
    And hub "hub-03" has capacity <capacity>

    Examples:
      | held | outcome  | capacity |
      | 9    | accepted | 9        |
      | 10   | refused  | 10       |

  # ---------------------------------------------------------------- Registering vehicles

  @BR14 @BR16
  Scenario: The operator registers a vehicle in a hub with a free slot
    Given hub "hub-03" with capacity 10 holds 9 vehicles
    When the operator registers vehicle "bike-0009" of type "E_BIKE" at hub "hub-03"
    Then vehicle "bike-0009" is "Deactivated" at hub "hub-03"
    And hub "hub-03" holds 10 vehicles

  @BR14
  Scenario: A vehicle cannot be registered in a full hub
    Given hub "hub-03" with capacity 10 holds 10 vehicles
    When the operator registers vehicle "bike-0009" of type "E_BIKE" at hub "hub-03"
    Then the registration is refused because the hub is full
    And vehicle "bike-0009" does not exist

  @BR14
  Scenario Outline: Only vehicles physically at a hub take a slot
    Given hub "hub-03" has capacity 1 and its only vehicle "bike-0010" is "<state>"
    When the operator registers vehicle "bike-0009" of type "E_BIKE" at hub "hub-03"
    Then the registration is <outcome>

    Examples:
      | state       | outcome  |
      | Deactivated | refused  |
      | Available   | refused  |
      | Reserved    | refused  |
      | Maintenance | refused  |
      | InUse       | accepted |
      | InTransit   | accepted |
      | Retired     | accepted |

  Scenario: A vehicle code can only be used once
    Given vehicle "bike-0009" is "Deactivated"
    When the operator registers vehicle "bike-0009" of type "E_SCOOTER" at hub "hub-03"
    Then the registration is refused because the code is already in use

  # ---------------------------------------------------------------- Activation and deactivation

  @BR16
  Scenario: Activating a vehicle with no open ticket makes it Available
    Given vehicle "bike-0009" is "Deactivated"
    When the operator activates vehicle "bike-0009"
    Then vehicle "bike-0009" is "Available" at hub "hub-03"

  @BR16
  Scenario Outline: A vehicle cannot be deactivated while Reserved or InUse
    Given vehicle "bike-0009" is "<state>"
    When the operator deactivates vehicle "bike-0009"
    Then the transition is <outcome>
    And vehicle "bike-0009" is "<final state>"

    Examples:
      | state     | outcome  | final state |
      | Available | accepted | Deactivated |
      | Reserved  | refused  | Reserved    |
      | InUse     | refused  | InUse       |

  # ---------------------------------------------------------------- Relocation

  Scenario: The operator moves a vehicle to another hub
    Given vehicle "bike-0009" is "Available"
    And hub "hub-05" with capacity 10 holds 9 vehicles
    When the operator starts moving vehicle "bike-0009"
    Then vehicle "bike-0009" is "InTransit" and belongs to no hub
    And hub "hub-03" holds 0 vehicles
    When the operator confirms the arrival of vehicle "bike-0009" at hub "hub-05"
    Then vehicle "bike-0009" is "Available" at hub "hub-05"
    And hub "hub-05" holds 10 vehicles

  @BR14
  Scenario: Arrival at a full hub is refused
    Given vehicle "bike-0009" is "InTransit"
    And hub "hub-05" with capacity 10 holds 10 vehicles
    When the operator confirms the arrival of vehicle "bike-0009" at hub "hub-05"
    Then the transition is refused because the hub is full
    And vehicle "bike-0009" is "InTransit" and belongs to no hub

  @BR16
  Scenario Outline: Only an Available vehicle can be moved
    Given vehicle "bike-0009" is "<state>"
    When the operator starts moving vehicle "bike-0009"
    Then the transition is <outcome>

    Examples:
      | state       | outcome  |
      | Available   | accepted |
      | Deactivated | refused  |
      | Reserved    | refused  |
      | InUse       | refused  |
      | Maintenance | refused  |

  # ---------------------------------------------------------------- Retirement

  @BR16
  Scenario Outline: A vehicle can be retired only when it has no active booking
    Given vehicle "bike-0009" is "<state>"
    When the operator retires vehicle "bike-0009"
    Then the transition is <outcome>
    And vehicle "bike-0009" is "<final state>"

    Examples:
      | state       | outcome  | final state |
      | Deactivated | accepted | Retired     |
      | Available   | accepted | Retired     |
      | Maintenance | accepted | Retired     |
      | Reserved    | refused  | Reserved    |
      | InUse       | refused  | InUse       |
      | InTransit   | refused  | InTransit   |

  @BR16
  Scenario Outline: No transition out of Retired is allowed
    Given vehicle "bike-0009" is "Retired"
    When the operator asks to <action> vehicle "bike-0009"
    Then the transition is refused
    And vehicle "bike-0009" is "Retired"

    Examples:
      | action       |
      | activate     |
      | deactivate   |
      | start moving |
      | retire       |

  # ---------------------------------------------------------------- State history

  @BR16
  Scenario: Every allowed transition is recorded in the state history, refused ones are not
    Given hub "hub-03" with capacity 10 exists
    And hub "hub-05" with capacity 10 exists
    When the operator registers vehicle "bike-0009" of type "E_BIKE" at hub "hub-03"
    And the operator activates vehicle "bike-0009"
    And the operator starts moving vehicle "bike-0009"
    And the operator activates vehicle "bike-0009"
    And the operator confirms the arrival of vehicle "bike-0009" at hub "hub-05"
    And the operator retires vehicle "bike-0009"
    Then the state history of vehicle "bike-0009" is, in order:
      | from        | to          |
      |             | Deactivated |
      | Deactivated | Available   |
      | Available   | InTransit   |
      | InTransit   | Available   |
      | Available   | Retired     |
