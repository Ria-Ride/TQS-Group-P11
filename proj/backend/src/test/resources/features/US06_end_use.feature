@REQ_KAN-58
Feature: End a ride at a hub and pay any overtime from the deposit (US06)
  As a tourist
  I want to end my use at a hub and see any overtime charge deducted from my deposit
  So that I stop being charged and there are no surprises

  # The rules of use in force are those of the booking tests (TestRules): the deposit is 30.00 EUR
  # for an E_BIKE (BR11); overtime starts after 10 minutes of grace and costs 2.00 EUR per started
  # block of 15 minutes (BR12); after a refused end at a full hub, the tourist has 15 minutes to end
  # at another hub (BR15).
  # The platform clock is fixed at "now" and moved only by the steps. Every ride here starts now and
  # is planned for 5 minutes, so "N minutes late" is N minutes after its planned end.
  # The vehicles are simulated on the MQTT broker: they answer a lock command with COMPLETED unless
  # the step says otherwise.
  # Hubs: hub-03 and hub-05 are 300 m apart; hub-09 is about 3 km away; hub-07 is full (1 of 1).

  Background:
    Given the rules of use in force are those of the booking tests
    And the platform clock is fixed
    And the vehicles answer lock commands
    And tourist "Ana" is logged in
    And "Ana" has a valid "DAY" pass
    And these hubs exist:
      | code   | latitude | longitude | capacity | vehicles |
      | hub-03 | 40.6405  | -8.6538   | 10       | 0        |
      | hub-05 | 40.6432  | -8.6538   | 10       | 0        |
      | hub-09 | 40.6675  | -8.6538   | 10       | 0        |
      | hub-07 | 40.6410  | -8.6540   | 1        | 1        |
    And hub "hub-03" holds these available vehicles:
      | code      | type   | battery |
      | bike-0001 | E_BIKE | 100     |
    And "Ana" has started using "bike-0001" for 5 minutes

  # ---------------------------------------------------------------- ending (KAN-711)

  @BR14
  Scenario Outline: Ending at a hub with a free slot locks the vehicle and makes it Available there
    When it is 0 minutes late
    And "Ana" ends the ride at hub "<hub>"
    Then the ride has ended at hub "<hub>"
    And vehicle "bike-0001" is "Available" at hub "<hub>"
    And vehicle "bike-0001" receives a lock command

    Examples: The start hub, or any other hub
      | hub    |
      | hub-03 |
      | hub-05 |

  Scenario: A lock the vehicle rejects leaves the ride going
    Given vehicle "bike-0001" will answer the lock with REJECTED "NOT_AT_HUB"
    When it is 0 minutes late
    And "Ana" ends the ride at hub "hub-05"
    Then the end is refused because the vehicle could not be locked
    And the booking is "ACTIVE"
    And vehicle "bike-0001" is "InUse"
    And the deposit of the booking is still authorized

  Scenario: A lock the vehicle does not answer leaves the ride going
    Given vehicle "bike-0001" will not answer the lock
    When it is 0 minutes late
    And "Ana" ends the ride at hub "hub-05"
    Then the end is refused because the vehicle did not answer in time
    And the booking is "ACTIVE"
    And the deposit of the booking is still authorized

  @BR23
  Scenario: A tourist cannot end another tourist's ride
    Given tourist "Bruno" is logged in
    When "Bruno" ends the ride at hub "hub-05"
    Then access to the booking is denied
    And the booking is "ACTIVE"

  # ---------------------------------------------------------------- overtime and deposit (KAN-710)

  @BR12 @BR13
  Scenario Outline: Overtime is charged per started block after the grace, and taken from the deposit
    When it is <late> minutes late
    And "Ana" ends the ride at hub "hub-03"
    Then the overtime charge is <charge> EUR
    And <captured> EUR of the deposit is captured and <released> EUR released

    Examples: Grace 10 min, block 15 min, 2.00 EUR per block, deposit 30.00 EUR
      | late | charge | captured | released |
      | 0    | 0.00   | 0.00     | 30.00    |
      | 10   | 0.00   | 0.00     | 30.00    |
      | 11   | 2.00   | 2.00     | 28.00    |
      | 25   | 2.00   | 2.00     | 28.00    |
      | 26   | 4.00   | 4.00     | 26.00    |
      | 40   | 4.00   | 4.00     | 26.00    |
      | 251  | 34.00  | 30.00    | 0.00     |

  # ---------------------------------------------------------------- full hubs (KAN-712)

  @BR14 @BR15
  Scenario: A full hub refuses the end, records the attempt and suggests nearby hubs with room
    When it is 20 minutes late
    And "Ana" ends the ride at hub "hub-07"
    Then the end is refused because the hub is full
    And these hubs are suggested, nearest first: "hub-03, hub-05, hub-09"
    And the booking is "ACTIVE"
    And the first full-hub attempt is recorded 20 minutes late
    And the deposit of the booking is still authorized

  @BR15
  Scenario: A second refused attempt does not move the first one
    Given it is 20 minutes late
    And "Ana" has tried to end the ride at the full hub "hub-07"
    When it is 30 minutes late
    And "Ana" ends the ride at hub "hub-07"
    Then the end is refused because the hub is full
    And the first full-hub attempt is recorded 20 minutes late

  @BR15
  Scenario Outline: After a full hub, overtime counts only until the attempt if the tourist ends within 15 minutes
    Given it is 20 minutes late
    And "Ana" has tried to end the ride at the full hub "hub-07"
    When it is <late> minutes late
    And "Ana" ends the ride at hub "hub-05"
    Then the overtime charge is <charge> EUR

    # Attempt 20 minutes late (2.00 EUR); ended 15 minutes after it still counts until the attempt;
    # 16 minutes after it counts until the real end (36 minutes late: 4.00 EUR).
    Examples:
      | late | charge |
      | 30   | 2.00   |
      | 35   | 2.00   |
      | 36   | 4.00   |
