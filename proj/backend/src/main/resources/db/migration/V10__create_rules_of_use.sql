-- US14 (KAN-66): the rules of use the client manager sets, kept as versions (BR21). A change adds
-- the next version and never edits an old one, so a booking can keep the version it was made with.
-- Durations are whole minutes. created_by is the account that made the version, NULL for the
-- first one, which is seeded here; it has no foreign key, because the version must outlive the
-- account (it is a record of who changed what).

CREATE TABLE rules_of_use (
    version                      INTEGER                  PRIMARY KEY,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by                   UUID,
    max_booking_minutes          INTEGER                  NOT NULL,
    min_battery_percent          INTEGER                  NOT NULL,
    start_window_minutes         INTEGER                  NOT NULL,
    repositioning_window_minutes INTEGER                  NOT NULL,
    overtime_grace_minutes       INTEGER                  NOT NULL,
    overtime_block_minutes       INTEGER                  NOT NULL,
    overtime_rate_per_block      NUMERIC(10, 2)           NOT NULL,
    CONSTRAINT ck_rules_version CHECK (version >= 1),
    -- BR21: the longest booking is more than 0 and at most 24 hours.
    CONSTRAINT ck_rules_max_booking CHECK (max_booking_minutes BETWEEN 1 AND 1440),
    -- BR21: the minimum battery is from 0 to 100 percent.
    CONSTRAINT ck_rules_min_battery CHECK (min_battery_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_rules_start_window CHECK (start_window_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_rules_repositioning CHECK (repositioning_window_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_rules_overtime_grace CHECK (overtime_grace_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_rules_overtime_block CHECK (overtime_block_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_rules_overtime_rate CHECK (overtime_rate_per_block >= 0)
);

-- What a version says about each vehicle type: the deposit and the battery a started 30 minutes of
-- riding uses (BR1).
CREATE TABLE rules_of_use_vehicle_types (
    version                         INTEGER        NOT NULL,
    vehicle_type                    VARCHAR(20)    NOT NULL,
    deposit                         NUMERIC(10, 2) NOT NULL,
    battery_percent_per_30_minutes  INTEGER        NOT NULL,
    CONSTRAINT pk_rules_of_use_vehicle_types PRIMARY KEY (version, vehicle_type),
    CONSTRAINT fk_rules_of_use_vehicle_types_version FOREIGN KEY (version)
        REFERENCES rules_of_use (version),
    CONSTRAINT ck_rules_vehicle_type CHECK (vehicle_type IN ('E_BIKE', 'E_SCOOTER')),
    CONSTRAINT ck_rules_deposit CHECK (deposit >= 0),
    CONSTRAINT ck_rules_battery_rate CHECK (battery_percent_per_30_minutes BETWEEN 0 AND 100)
);

-- Version 1: the values the platform used before the client could change them. The minimum battery
-- and the rate (20% and 5% per started 30 minutes) are those of the vehicle search (US02, BR1).
-- The other values are placeholders until the client sets its own: longest booking 8 hours,
-- reservation start window 15 minutes, repositioning window 10 minutes, overtime grace 5 minutes,
-- blocks of 15 minutes at 1.50 each, deposits of 20.00 (e-bike) and 30.00 (e-scooter).
INSERT INTO rules_of_use (
    version, created_at, created_by, max_booking_minutes, min_battery_percent,
    start_window_minutes, repositioning_window_minutes, overtime_grace_minutes,
    overtime_block_minutes, overtime_rate_per_block)
VALUES (1, TIMESTAMP WITH TIME ZONE '2026-11-24 00:00:00+00', NULL, 480, 20, 15, 10, 5, 15, 1.50);

INSERT INTO rules_of_use_vehicle_types (version, vehicle_type, deposit, battery_percent_per_30_minutes)
VALUES (1, 'E_BIKE', 20.00, 5),
       (1, 'E_SCOOTER', 30.00, 5);
