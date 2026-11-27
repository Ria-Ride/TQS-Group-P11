@REQ_KAN-56
Feature: Start an immediate use of an available vehicle (US04)
  As a tourist
  I want to start an immediate use of an available vehicle
  So that I can ride right away and know the charges upfront

  # The rules of use in force are those of the booking tests (TestRules), saved as a new version
  # over the seeded one, which the client manager may change (US14): maxBookingDuration is 4 hours;
  # the battery a ride needs is the 20% floor plus 5% per started 30 minutes (BR1). The deposit is
  # 30.00 EUR for an E_BIKE and 50.00 EUR for an E_SCOOTER (BR11). The tourist's test card at the
  # simulated gateway decides whether the deposit is authorized, declined or never answered; a
  # start uses an approving card unless the step says otherwise.
  # The Pass used is chosen by the platform: one valid now that allows the vehicle type. The planned
  # end must not be after the Pass's validTo (BR4).
  # A Reserved Booking cannot be created yet (US05), so BR6 is shown here with an Active one.
  # Battery levels and vehicle states are written to the database as a telemetry update would.

  Background:
    Given the rules of use in force are those of the booking tests
    And tourist "Ana" is logged in
    And hub "hub-03" exists
    And hub "hub-03" holds these available vehicles:
      | code         | type      | battery |
      | bike-0001    | E_BIKE    | 100     |
      | bike-0002    | E_BIKE    | 100     |
      | scooter-0001 | E_SCOOTER | 100     |

  @BR1 @BR2 @BR4 @BR11
  Scenario Outline: A valid start makes the Booking Active, the vehicle InUse and holds the deposit
    Given "Ana" has a valid "DAY" pass
    When "Ana" starts using "<vehicle>" for 60 minutes
    Then "Ana" has an Active booking of "<vehicle>" starting at hub "hub-03"
    And a deposit of <deposit> EUR is authorized for the booking
    And vehicle "<vehicle>" is "InUse"
    And the state history of "<vehicle>" records the change from Available to InUse

    Examples:
      | vehicle      | deposit |
      | bike-0001    | 30.00   |
      | scooter-0001 | 50.00   |

  Scenario: The vehicle receives the unlock command
    Given "Ana" has a valid "DAY" pass
    When "Ana" starts using "bike-0001" for 60 minutes
    Then vehicle "bike-0001" receives an unlock command

  @BR4
  Scenario: A Pass that does not allow the vehicle type refuses the start
    Given "Ana" has a valid pass that allows only "E_BIKE"
    When "Ana" starts using "scooter-0001" for 60 minutes
    Then the start is refused because the pass does not allow the vehicle type
    And no deposit is authorized for "Ana"
    And vehicle "scooter-0001" is "Available"

  @BR4
  Scenario: A tourist with no valid Pass cannot start
    When "Ana" starts using "bike-0001" for 60 minutes
    Then the start is refused because there is no valid pass
    And vehicle "bike-0001" is "Available"

  @BR4
  Scenario Outline: The ride must end before the Pass does
    Given "Ana" has a valid "DAY" pass that ends in 60 minutes
    When "Ana" starts using "bike-0001" for <minutes> minutes
    Then the start is <outcome>

    Examples: Boundary of the Pass's validTo
      | minutes | outcome                                       |
      | 60      | accepted                                      |
      | 61      | refused because the ride ends after the pass  |

  @BR2
  Scenario Outline: A vehicle that is not Available cannot be started
    Given "Ana" has a valid "DAY" pass
    And hub "hub-03" holds vehicle "bike-0009" in state "<state>" with a battery of 100%
    When "Ana" starts using "bike-0009" for 60 minutes
    Then the start is refused because the vehicle is not available
    And no deposit is authorized for "Ana"

    Examples:
      | state       |
      | Deactivated |
      | Reserved    |
      | Maintenance |

  @BR1
  Scenario Outline: The vehicle needs enough battery for the planned ride
    Given "Ana" has a valid "DAY" pass
    And hub "hub-03" holds these available vehicles:
      | code      | type   | battery   |
      | bike-0010 | E_BIKE | <battery> |
    When "Ana" starts using "bike-0010" for 60 minutes
    Then the start is <outcome>

    Examples: 60 minutes need 20% + 2 x 5% = 30%
      | battery | outcome                                      |
      | 29      | refused because the battery is not enough    |
      | 30      | accepted                                     |

  @BR5
  Scenario Outline: The planned duration must be above 0 and at most maxBookingDuration
    Given "Ana" has a valid "MULTI_DAY" pass
    When "Ana" starts using "bike-0001" for <minutes> minutes
    Then the start is <outcome>

    Examples: Boundaries of the 4-hour limit
      | minutes | outcome                                         |
      | 0       | refused because the duration is not allowed     |
      | 1       | accepted                                        |
      | 240     | accepted                                        |
      | 241     | refused because the duration is not allowed     |

  @BR6
  Scenario: A tourist with an ongoing Booking cannot start another one
    Given "Ana" has a valid "DAY" pass
    And "Ana" has started using "bike-0002" for 60 minutes
    When "Ana" starts using "bike-0001" for 30 minutes
    Then the start is refused because "Ana" already has a booking in that period
    And vehicle "bike-0001" is "Available"

  @BR11
  Scenario Outline: A deposit that is not authorized creates no Booking and changes no vehicle
    Given "Ana" has a valid "DAY" pass
    When "Ana" starts using "bike-0001" for 60 minutes and the deposit is <outcome>
    Then the start is refused because the deposit was <outcome>
    And "Ana" has no booking
    And vehicle "bike-0001" is "Available"

    Examples:
      | outcome   |
      | declined  |
      | timed out |

  @BR3 @BR11
  Scenario Outline: Only one of several simultaneous starts for the same vehicle succeeds
    Given <tourists> tourists with a valid "DAY" pass
    When they all start using "bike-0001" for 60 minutes at the same time
    Then exactly one of them has an Active booking of "bike-0001"
    And the other <losers> are refused because the vehicle is not available
    And the deposit of every refused tourist is released

    Examples:
      | tourists | losers |
      | 2        | 1      |
      | 10       | 9      |