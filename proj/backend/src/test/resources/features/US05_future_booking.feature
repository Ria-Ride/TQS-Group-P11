@REQ_KAN-57
Feature: Book a vehicle for a future period, start it at the hub or cancel it (US05)
  As a tourist
  I want to book a vehicle for a future period, start it at the hub and cancel it if my plans change
  So that I can guarantee a vehicle when I need it

  # The rules of use in force are those of the booking tests (TestRules): the longest booking is
  # 4 hours, the deposit is 30.00 EUR for an E_BIKE and 50.00 EUR for an E_SCOOTER (BR11), and a
  # reservation can be started from its planned start up to 15 minutes later (BR7).
  # The platform clock is fixed at the start of every scenario ("now"), and only the steps move it,
  # so the time boundaries never depend on how fast the steps run. Times are minutes from "now".
  # Bookings use an approving test card unless the step says otherwise. Scenarios that move the
  # clock to the start window book from minute 10: access tokens last one hour on the same clock.

  Background:
    Given the rules of use in force are those of the booking tests
    And the platform clock is fixed
    And tourist "Ana" is logged in
    And hub "hub-03" exists
    And hub "hub-03" holds these available vehicles:
      | code         | type      | battery |
      | bike-0001    | E_BIKE    | 100     |
      | bike-0002    | E_BIKE    | 100     |
      | scooter-0001 | E_SCOOTER | 100     |

  # ---------------------------------------------------------------- booking (KAN-705)

  @BR2 @BR4 @BR11
  Scenario Outline: A free period within the Pass makes the Booking and the vehicle Reserved
    Given "Ana" has a valid "DAY" pass
    When "Ana" books "<vehicle>" from minute 60 to minute 120
    Then "Ana" has a Reserved booking of "<vehicle>" at hub "hub-03"
    And a deposit of <deposit> EUR is authorized for the booking
    And vehicle "<vehicle>" is "Reserved"
    And the state history of "<vehicle>" records the change from Available to Reserved

    Examples:
      | vehicle      | deposit |
      | bike-0001    | 30.00   |
      | scooter-0001 | 50.00   |

  @BR4
  Scenario Outline: The booking must end before the Pass does
    Given "Ana" has a valid "DAY" pass that ends in 180 minutes
    When "Ana" books "bike-0001" from minute 120 to minute <end>
    Then the booking is <outcome>

    # US04 accepts a ride that ends exactly when the Pass ends; a reservation follows the same rule.
    Examples: Boundary of the Pass's validTo (minute 180)
      | end | outcome                                      |
      | 179 | accepted                                     |
      | 180 | accepted                                     |
      | 181 | refused because the ride ends after the pass |

  @BR4
  Scenario: A Pass that is not valid yet when the booking starts refuses it
    Given "Ana" has a "DAY" pass valid from minute 120 to minute 1560
    When "Ana" books "bike-0001" from minute 60 to minute 90
    Then the booking is refused because there is no valid pass
    And no deposit is authorized for "Ana"
    And vehicle "bike-0001" is "Available"

  @BR6
  Scenario Outline: A booking may not overlap another of the tourist's bookings
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0002" from minute 60 to minute 120
    When "Ana" books "bike-0001" from minute <start> to minute <end>
    Then the booking is <outcome>

    Examples: Back-to-back periods do not overlap
      | start | end | outcome                                                  |
      | 30    | 60  | accepted                                                 |
      | 30    | 61  | refused because "Ana" already has a booking in that period |
      | 119   | 150 | refused because "Ana" already has a booking in that period |
      | 120   | 180 | accepted                                                 |

  @BR2 @BR3
  Scenario: A Reserved vehicle cannot be booked by another tourist
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0001" from minute 60 to minute 120
    And tourist "Bruno" is logged in
    And "Bruno" has a valid "DAY" pass
    When "Bruno" books "bike-0001" from minute 200 to minute 260
    Then the booking is refused because the vehicle is not available
    And no deposit is authorized for "Bruno"

  @BR11
  Scenario: A declined deposit creates no Booking and leaves the vehicle Available
    Given "Ana" has a valid "DAY" pass
    When "Ana" books "bike-0001" from minute 60 to minute 120 and the deposit is declined
    Then the booking is refused because the deposit was declined
    And "Ana" has no booking
    And vehicle "bike-0001" is "Available"

  # ---------------------------------------------------------------- start window (KAN-706)

  @BR7
  Scenario Outline: A Reserved Booking can be started from its planned start up to 15 minutes later
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0001" from minute 10 to minute 70
    When it is <offset> after the planned start of the booking
    And "Ana" starts the booking
    Then the start of the booking is <outcome>

    Examples: Boundaries of the start window
      | offset             | outcome                                       |
      | 1 minute before    | refused because it is too early               |
      | 0 minutes          | accepted                                      |
      | 15 minutes         | accepted                                      |
      | 15 minutes 1 second | refused because the start window has passed  |

  @BR7
  Scenario: Starting a Reserved Booking makes it Active and the vehicle InUse, and unlocks it
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0001" from minute 10 to minute 70
    When it is 5 minutes after the planned start of the booking
    And "Ana" starts the booking
    Then the booking is "ACTIVE"
    And vehicle "bike-0001" is "InUse"
    And vehicle "bike-0001" receives an unlock command
    And the deposit of the booking is still authorized

  @BR7 @BR13
  Scenario Outline: A Reserved Booking not started in its window expires and gives everything back
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0001" from minute 10 to minute 70
    When it is <offset> after the planned start of the booking
    And the expiry of reservations runs
    Then the booking is "<state>"
    And vehicle "bike-0001" is "<vehicle state>"
    And the deposit of the booking is <deposit>

    Examples: Expired at +15:01, not at +15:00
      | offset              | state    | vehicle state | deposit          |
      | 15 minutes          | RESERVED | Reserved      | still authorized |
      | 15 minutes 1 second | EXPIRED  | Available     | released         |

  @BR7
  Scenario: The expiry of reservations can run twice without changing anything the second time
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0001" from minute 10 to minute 70
    When it is 16 minutes after the planned start of the booking
    And the expiry of reservations runs
    And the expiry of reservations runs
    Then the booking is "EXPIRED"
    And the deposit of the booking is released

  # ---------------------------------------------------------------- cancellation (KAN-707)

  @BR13
  Scenario: Cancelling a Reserved Booking frees the vehicle and releases the whole deposit
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0001" from minute 60 to minute 120
    When "Ana" cancels the booking
    Then the booking is "CANCELLED"
    And vehicle "bike-0001" is "Available"
    And the deposit of the booking is released
    And the state history of "bike-0001" records the change from Reserved to Available

  @BR13
  Scenario: An Active Booking cannot be cancelled
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0001" from minute 10 to minute 70
    And it is 0 minutes after the planned start of the booking
    And "Ana" has started the booking
    When "Ana" cancels the booking
    Then the cancellation is refused because the booking is not Reserved
    And the booking is "ACTIVE"

  @BR23
  Scenario: A tourist cannot cancel or start another tourist's Booking
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0001" from minute 60 to minute 120
    And tourist "Bruno" is logged in
    When "Bruno" cancels the booking
    Then access to the booking is denied
    And the booking is "RESERVED"

  @BR18
  Scenario: A fault of a Reserved vehicle cancels its Booking and releases the deposit
    Given "Ana" has a valid "DAY" pass
    And "Ana" has booked "bike-0001" from minute 60 to minute 120
    When the simulator reports fault "BRAKE_WEAR" for "bike-0001" at "2026-11-20T10:00:00Z"
    Then the booking is "CANCELLED" with reason "vehicle fault"
    And the deposit of the booking is released
    And vehicle "bike-0001" is "Maintenance"
