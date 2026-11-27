@REQ_KAN-66
Feature: The client manager defines the rules of use, deposits, hub capacities, pass types and stay codes (US14)
  As an institutional client manager
  I want to define the rules of use, hub capacities, deposits, pass types and stay codes
  So that the service matches my guest-experience policy

  # Every change of the rules of use makes a new version and leaves the old ones as they were, so
  # it only applies to bookings created afterwards (BR21). Bookings come with US04; the versions
  # and the effect on the vehicle search (BR1) are shown here.
  # Money is written as a string ("1.50"), as the API does.
  # Hotels and pass types made by a scenario are removed afterwards (names and codes start with
  # CUKE), so the seeded ones stay the only ones for the next scenario.

  Background:
    Given a client manager is logged in

  # ---------------------------------------------------------------- BR21: rules of use

  @BR21
  Scenario: Valid rules of use are saved as a new version
    When the client manager sets the rules of use:
      | maxBookingMinutes | minBatteryPercent | startWindowMinutes | repositioningWindowMinutes | overtimeGraceMinutes | overtimeBlockMinutes | overtimeRatePerBlock | bikePercentPer30Minutes | scooterPercentPer30Minutes |
      | 240               | 25                | 20                 | 12                         | 0                    | 30                   | 2.00                 | 4                       | 9                          |
    Then the rules of use are at version 2
    And the longest booking is 240 minutes and the minimum battery is 25%

  @BR21
  Scenario Outline: A value outside its limits is refused and the rules stay as they were
    When the client manager sets the rules of use with <field> at <value>
    Then the rules of use are refused because of <field>
    And the rules of use are at version 1

    Examples: Longest booking, more than 0 and at most 24 hours
      | field             | value |
      | maxBookingMinutes | 0     |
      | maxBookingMinutes | 1441  |

    Examples: Minimum battery, from 0 to 100
      | field             | value |
      | minBatteryPercent | -1    |
      | minBatteryPercent | 101   |

  @BR21
  Scenario Outline: The limits themselves are accepted
    When the client manager sets the rules of use with <field> at <value>
    Then the rules of use are at version 2

    Examples:
      | field             | value |
      | maxBookingMinutes | 1     |
      | maxBookingMinutes | 1440  |
      | minBatteryPercent | 0     |
      | minBatteryPercent | 100   |

  @BR21
  Scenario: A change keeps the earlier version as it was
    Given the client manager has set the rules of use with maxBookingMinutes at 240
    When the client manager sets the rules of use with maxBookingMinutes at 120
    Then version 1 of the rules of use has a longest booking of 480 minutes
    And version 2 of the rules of use has a longest booking of 240 minutes
    And version 3 of the rules of use has a longest booking of 120 minutes

  # ---------------------------------------------------------------- BR4: deposits

  @BR4
  Scenario: The client manager sets the deposit of a vehicle type
    When the client manager sets the deposit of "E_SCOOTER" to "45.00"
    Then the deposit of "E_SCOOTER" is "45.00"
    And the deposit of "E_BIKE" is "20.00"

  @BR4
  Scenario Outline: An invalid deposit is refused
    When the client manager sets the deposit of "E_BIKE" to "<deposit>"
    Then the deposit is refused as invalid
    And the deposit of "E_BIKE" is "20.00"

    Examples:
      | deposit |
      | -1      |
      | 10.123  |

  # ---------------------------------------------------------------- BR14: hub capacity

  @BR14
  Scenario: The client manager changes the capacity of a hub
    Given an operator has registered hub "hub-03" with capacity 10 and 3 vehicles
    When the client manager changes the capacity of hub "hub-03" to 20
    Then hub "hub-03" has capacity 20 for the client manager

  @BR14
  Scenario Outline: A capacity below the vehicles the hub holds is refused
    Given an operator has registered hub "hub-03" with capacity 10 and 3 vehicles
    When the client manager changes the capacity of hub "hub-03" to <capacity>
    Then the change of capacity is <outcome>

    Examples:
      | capacity | outcome  |
      | 3        | accepted |
      | 2        | refused  |

  # ---------------------------------------------------------------- BR8: pass types

  @BR8
  Scenario: The client manager creates a pass type
    When the client manager creates pass type "CUKE_WEEK" named "Week pass" valid 7 days at "40.00" for "E_BIKE"
    Then pass type "CUKE_WEEK" is valid 7 days and costs "40.00"

  @BR8
  Scenario: The client manager changes the price of a pass type
    Given the client manager has created pass type "CUKE_WEEK" named "Week pass" valid 7 days at "40.00" for "E_BIKE"
    When the client manager changes the price of pass type "CUKE_WEEK" to "45.50"
    Then pass type "CUKE_WEEK" is valid 7 days and costs "45.50"

  @BR8
  Scenario: A pass type code that is already in use is refused
    When the client manager creates pass type "DAY" named "Another day pass" valid 1 days at "5.00" for "E_BIKE"
    Then the pass type is refused because the code is already in use

  @BR8
  Scenario Outline: An invalid pass type is refused
    When the client manager creates pass type "CUKE_BAD" named "Bad pass" valid <days> days at "<price>" for "E_BIKE"
    Then the pass type is refused as invalid
    And there is no pass type "CUKE_BAD"

    Examples:
      | days | price |
      | 0    | 10.00 |
      | 7    | -1    |

  # ---------------------------------------------------------------- BR9: stay codes

  @BR9
  Scenario: The client manager issues a stay code with check-in, check-out and discount
    Given the client manager has registered hotel "CUKE Hotel Laguna" with a discount of 20%
    When the client manager issues a stay code for hotel "CUKE Hotel Laguna" from "yesterday" to "tomorrow"
    Then a stay code is issued with check-in "yesterday", check-out "tomorrow" and a discount of 20%

  @BR9
  Scenario: A tourist with the issued code gets the discount of the hotel
    Given the client manager has registered hotel "CUKE Hotel Laguna" with a discount of 20%
    And the client manager has issued a stay code for hotel "CUKE Hotel Laguna" from "yesterday" to "tomorrow"
    Then a tourist quoted the "DAY" pass with that code pays "8.00"

  @BR9
  Scenario: A stay code is not issued when check-out is before check-in
    Given the client manager has registered hotel "CUKE Hotel Laguna" with a discount of 20%
    When the client manager issues a stay code for hotel "CUKE Hotel Laguna" from "tomorrow" to "yesterday"
    Then the stay code is refused because of checkOut

  @BR9
  Scenario: A hotel name that is already in use is refused
    Given the client manager has registered hotel "CUKE Hotel Laguna" with a discount of 20%
    When the client manager registers hotel "CUKE Hotel Laguna" with a discount of 5%
    Then the hotel is refused because the name is already in use

  # ---------------------------------------------------------------- BR21, BR23: other roles

  @BR21 @BR23
  Scenario Outline: Another role cannot edit what the client manager edits
    When the <role> tries to edit <what>
    Then the edit is refused as forbidden

    Examples:
      | role     | what                |
      | operator | the rules of use    |
      | operator | a deposit           |
      | operator | a pass type         |
      | operator | a stay code         |
      | tourist  | the rules of use    |
      | tourist  | a deposit           |
      | tourist  | a pass type         |
      | tourist  | a stay code         |
