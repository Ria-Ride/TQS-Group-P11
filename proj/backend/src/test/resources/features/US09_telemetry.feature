@REQ_KAN-61
Feature: Telemetry updates the state of the vehicles (US09)
  As a service operator
  I want the platform to receive telemetry from a fleet simulator and apply it to vehicle state
  So that I always know the real state of each vehicle

  # The readings are published on a real MQTT broker, as the simulator does (contract 1.0.0).
  # BR17: a reading is applied only if the battery is from 0 to 100, the vehicle is known and the
  # timestamp is newer than the last one. Otherwise it is discarded (and logged by the platform).
  # A discard is shown by a second reading for another vehicle that is applied afterwards: the
  # platform handles messages in order, so the earlier one has been dealt with by then.
  # The 5 s at 10 readings per second of SLO2 is checked in TelemetryIngestionIT.

  Background:
    Given an operator is logged in
    And hub "hub-03" has capacity 10 and its only vehicle "bike-0001" is "Available"
    And an operator watches the fleet

  @BR17
  Scenario: A valid reading updates battery, position and lock state
    When the simulator publishes a reading for "bike-0001" with battery 78.5%, lock state "unlocked" at "2026-11-10T10:00:00Z"
    Then vehicle "bike-0001" shows a battery of 78%
    And vehicle "bike-0001" shows lock state "UNLOCKED" and position 40.6405, -8.6538

  @BR16
  Scenario: A reading does not change the lifecycle state of the vehicle
    When the simulator publishes a reading for "bike-0001" with battery 78.5%, lock state "locked" at "2026-11-10T10:00:00Z"
    Then vehicle "bike-0001" shows a battery of 78%
    And vehicle "bike-0001" is "Available" at hub "hub-03"

  @BR17
  Scenario Outline: A battery outside 0 to 100 is discarded
    When the simulator publishes a reading for "bike-0001" with battery <battery>%, lock state "locked" at "2026-11-10T10:00:00Z"
    Then the reading is discarded
    And vehicle "bike-0001" shows no battery reading

    Examples:
      | battery |
      | -1      |
      | 101     |

  @BR17
  Scenario Outline: A battery at the limits is accepted
    When the simulator publishes a reading for "bike-0001" with battery <battery>%, lock state "locked" at "2026-11-10T10:00:00Z"
    Then vehicle "bike-0001" shows a battery of <shown>%

    Examples:
      | battery | shown |
      | 0       | 0     |
      | 100     | 100   |

  @BR17
  Scenario: A reading for an unknown vehicle is discarded
    When the simulator publishes a reading for "ghost-0001" with battery 50%, lock state "locked" at "2026-11-10T10:00:00Z"
    Then the reading is discarded
    And vehicle "ghost-0001" does not exist

  @BR17
  Scenario Outline: A reading not newer than the last one is discarded
    Given the simulator has published a reading for "bike-0001" with battery 80%, lock state "locked" at "2026-11-10T10:00:10Z"
    When the simulator publishes a reading for "bike-0001" with battery 10%, lock state "locked" at "<timestamp>"
    Then the reading is discarded
    And vehicle "bike-0001" shows a battery of 80%

    Examples:
      | timestamp            |
      | 2026-11-10T10:00:10Z |
      | 2026-11-10T10:00:00Z |

  @BR17
  Scenario: A later reading replaces the earlier one
    Given the simulator has published a reading for "bike-0001" with battery 80%, lock state "locked" at "2026-11-10T10:00:10Z"
    When the simulator publishes a reading for "bike-0001" with battery 79%, lock state "unlocked" at "2026-11-10T10:00:20Z"
    Then vehicle "bike-0001" shows a battery of 79%

  Scenario: A message that is not valid telemetry is discarded
    When the simulator publishes a message that is not valid telemetry for "bike-0001"
    Then the reading is discarded
    And vehicle "bike-0001" shows no battery reading

  # US02, BR1: the search sees the new battery
  @BR1
  Scenario: A telemetry update changes the next search
    Given the simulator has published a reading for "bike-0001" with battery 80%, lock state "locked" at "2026-11-10T10:00:00Z"
    And a tourist searching hub "hub-03" sees vehicle "bike-0001" as bookable
    When the simulator publishes a reading for "bike-0001" with battery 19%, lock state "locked" at "2026-11-10T10:00:10Z"
    Then a tourist searching hub "hub-03" sees vehicle "bike-0001" as not bookable
