@REQ_KAN-54
Feature: Search available vehicles by hub and vehicle type (US02)
  As a tourist
  I want to search available vehicles by hub and vehicle type
  So that I can quickly find a vehicle with enough battery near the sights

  # The battery a ride needs (BR1) is the floor, minBatteryPercent (20% by default), plus 5% for every
# started 30 minutes of the ride, when the tourist gives its duration (1 to 1440 minutes). Without a
# duration only the floor applies: 19% is refused and 20% accepted.
  # Battery levels arrive by telemetry, which the backend does not receive yet (KAN-92), so the
  # steps write them to the database as a telemetry update would. Bookings and maintenance tickets
  # do not exist yet either, so Reserved and Maintenance are written the same way.
  # "Cannot be selected" (BR2) is shown by the result: bookable false and the reason.
  # The empty-state message belongs to the search page (KAN-93); the API returns an empty list.

  Background:
    Given a tourist is logged in
    And hub "hub-03" exists

  Scenario: The tourist sees the vehicles of a hub with their type and battery level
    Given hub "hub-03" holds these available vehicles:
      | code         | type      | battery |
      | bike-0001    | E_BIKE    | 80      |
      | scooter-0001 | E_SCOOTER | 55      |
    When the tourist searches hub "hub-03"
    Then the tourist sees these vehicles:
      | code         | type      | battery | bookable |
      | bike-0001    | E_BIKE    | 80      | yes      |
      | scooter-0001 | E_SCOOTER | 55      | yes      |

  Scenario Outline: The tourist filters by vehicle type
    Given hub "hub-03" holds these available vehicles:
      | code         | type      | battery |
      | bike-0001    | E_BIKE    | 80      |
      | scooter-0001 | E_SCOOTER | 55      |
    When the tourist searches hub "hub-03" for type "<type>"
    Then the tourist sees only vehicles of type "<type>"

    Examples:
      | type      |
      | E_BIKE    |
      | E_SCOOTER |

  @BR1
  Scenario Outline: A vehicle below the minimum battery is shown as not bookable
    Given hub "hub-03" holds these available vehicles:
      | code      | type   | battery   |
      | bike-0001 | E_BIKE | <battery> |
    When the tourist searches hub "hub-03"
    Then vehicle "bike-0001" is shown as <outcome>

    Examples: Boundary of the 20% minimum
      | battery | outcome      |
      | 19      | not bookable |
      | 20      | bookable     |

  # BR1 with the duration of the ride: 20% floor + 5% per started 30 minutes
  @BR1
  Scenario Outline: A longer ride needs more battery
    Given hub "hub-03" holds these available vehicles:
      | code      | type   | battery   |
      | bike-0001 | E_BIKE | <battery> |
    When the tourist searches hub "hub-03" to ride for <minutes> minutes
    Then vehicle "bike-0001" needs a battery of <needed>%
    And vehicle "bike-0001" is shown as <outcome>

    Examples: Every started 30 minutes counts
      | minutes | needed | battery | outcome      |
      | 30      | 25     | 24      | not bookable |
      | 30      | 25     | 25      | bookable     |
      | 31      | 30     | 29      | not bookable |
      | 31      | 30     | 30      | bookable     |
      | 120     | 40     | 40      | bookable     |

  @BR1
  Scenario: Without a duration only the floor is needed
    Given hub "hub-03" holds these available vehicles:
      | code      | type   | battery |
      | bike-0001 | E_BIKE | 20      |
    When the tourist searches hub "hub-03"
    Then vehicle "bike-0001" needs a battery of 20%
    And vehicle "bike-0001" is shown as bookable

  @BR1
  Scenario: A ride no battery can do leaves nothing bookable
    Given hub "hub-03" holds these available vehicles:
      | code      | type   | battery |
      | bike-0001 | E_BIKE | 100     |
    When the tourist searches hub "hub-03" to ride for 1440 minutes
    Then no vehicle is bookable

  Scenario Outline: A duration outside one minute to one day is refused
    When the tourist searches hub "hub-03" to ride for <minutes> minutes
    Then the search is refused as invalid

    Examples:
      | minutes |
      | 0       |
      | 1441    |

  # BR1, after a telemetry update
  @BR1
  Scenario: A telemetry update changes the result of the next search
    Given hub "hub-03" holds these available vehicles:
      | code      | type   | battery |
      | bike-0001 | E_BIKE | 80      |
    And the tourist searched hub "hub-03"
    When vehicle "bike-0001" reports a battery of 19%
    And the tourist searches hub "hub-03"
    Then vehicle "bike-0001" is shown as not bookable

  @BR1
  Scenario: A vehicle that has not reported its battery is not bookable
    Given hub "hub-03" holds an available vehicle "bike-0001" that has not reported its battery
    When the tourist searches hub "hub-03"
    Then vehicle "bike-0001" is shown as not bookable

  @BR2
  Scenario Outline: A vehicle that is not Available cannot be selected
    Given hub "hub-03" holds vehicle "bike-0001" in state "<state>" with a battery of 100%
    When the tourist searches hub "hub-03"
    Then vehicle "bike-0001" is shown as not bookable

    Examples:
      | state       |
      | Deactivated |
      | Reserved    |
      | Maintenance |

  Scenario: A hub with no vehicles gives an empty result
    When the tourist searches hub "hub-03"
    Then the tourist sees no vehicles

  @BR2
  Scenario: A hub whose vehicles are all unavailable has nothing to select
    Given hub "hub-03" holds vehicle "bike-0001" in state "Reserved" with a battery of 100%
    When the tourist searches hub "hub-03"
    Then no vehicle is bookable

  Scenario: An unknown hub is refused
    When the tourist searches a hub that does not exist
    Then the search is refused as not found
