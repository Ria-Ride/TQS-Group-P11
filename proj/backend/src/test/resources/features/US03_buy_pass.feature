@REQ_KAN-55
Feature: Buy a day or multi-day pass (US03)
  As a tourist staying at a partner hotel
  I want to buy a day or multi-day pass
  So that I can use the vehicles during my stay

  # Seeded pass types (KAN-98): DAY costs 10.00 EUR and lasts 1 day, MULTI_DAY costs 25.00 EUR
  # and lasts 3 days. A pass is valid from the moment the payment is approved for validityDays x 24
  # hours (BR8). The partner hotel "Hotel Ria Azul" gives 10% off, rounded half-up to the cent
  # (BR9). The payment goes through the simulated gateway; the tourist's test card decides whether
  # it is approved, declined or never answered (BR10). A purchase uses an approving card unless the
  # step says otherwise.
  # Stay dates are calendar days at the hotel (Europe/Lisbon); "today" is the hotel's today.

  Background:
    Given tourist "Ana" is logged in
    And the partner hotel "Hotel Ria Azul" gives 10% off

  Scenario: The tourist sees the pass types with their price and validity
    When "Ana" looks at the pass types
    Then "Ana" sees these pass types:
      | code      | price | validityDays |
      | DAY       | 10.00 | 1            |
      | MULTI_DAY | 25.00 | 3            |

  @BR8
  Scenario Outline: An approved payment issues a pass valid for its number of days
    When "Ana" buys a "<type>" pass
    Then "Ana" gets a "<type>" pass for <price> EUR
    And the pass is valid for <hours> hours from the moment of purchase

    Examples:
      | type      | price | hours |
      | DAY       | 10.00 | 24    |
      | MULTI_DAY | 25.00 | 72    |

  @BR10
  Scenario Outline: A payment that is not approved issues no pass
    When "Ana" buys a "DAY" pass and the payment is <outcome>
    Then the purchase is refused because the payment was <outcome>
    And "Ana" has no pass

    Examples:
      | outcome   |
      | declined  |
      | timed out |

  @BR9
  Scenario Outline: A valid stay code lowers the price by the hotel's discount
    Given stay code "RIA-1001" of "Hotel Ria Azul" runs from yesterday to tomorrow
    When "Ana" buys a "<type>" pass with stay code "RIA-1001"
    Then "Ana" gets a "<type>" pass for <price> EUR

    Examples: 10% off, rounded half-up
      | type      | price |
      | DAY       | 9.00  |
      | MULTI_DAY | 22.50 |

  @BR9
  Scenario Outline: A stay code is accepted only from check-in to check-out
    Given stay code "RIA-1001" of "Hotel Ria Azul" runs from <checkIn> to <checkOut>
    When "Ana" buys a "DAY" pass with stay code "RIA-1001"
    Then the stay code is <outcome>

    Examples:
      | checkIn    | checkOut   | outcome                     |
      | tomorrow   | in 3 days  | refused as not valid today  |
      | today      | in 2 days  | accepted                    |
      | 2 days ago | today      | accepted                    |
      | 3 days ago | yesterday  | refused as not valid today  |

  @BR9
  Scenario: A stay code linked to another account is refused
    Given tourist "Rui" is logged in
    And stay code "RIA-1001" of "Hotel Ria Azul" runs from yesterday to tomorrow
    And "Rui" has bought a "DAY" pass with stay code "RIA-1001"
    When "Ana" buys a "DAY" pass with stay code "RIA-1001"
    Then the stay code is refused as linked to another account
    And "Ana" has no pass

  @BR9
  Scenario: The tourist who redeemed a stay code can use it again during the stay
    Given stay code "RIA-1001" of "Hotel Ria Azul" runs from yesterday to tomorrow
    And "Ana" has bought a "DAY" pass with stay code "RIA-1001"
    When "Ana" buys a "MULTI_DAY" pass with stay code "RIA-1001"
    Then "Ana" gets a "MULTI_DAY" pass for 22.50 EUR

  @BR9 @BR10
  Scenario: A payment that is not approved does not link the stay code
    Given tourist "Rui" is logged in
    And stay code "RIA-1001" of "Hotel Ria Azul" runs from yesterday to tomorrow
    And "Rui" tried to buy a "DAY" pass with stay code "RIA-1001" but the payment was declined
    When "Ana" buys a "DAY" pass with stay code "RIA-1001"
    Then "Ana" gets a "DAY" pass for 9.00 EUR

  Scenario: An unknown stay code is refused and the tourist can buy at the base price
    When "Ana" buys a "DAY" pass with stay code "NOPE-0000"
    Then the stay code is refused as unknown
    And "Ana" has no pass
    When "Ana" buys a "DAY" pass
    Then "Ana" gets a "DAY" pass for 10.00 EUR
