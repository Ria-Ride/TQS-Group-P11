-- US04 (KAN-698): bookings, with the rules of use they keep from their creation (BR21), so a later
-- change by the client applies only to new bookings. Durations are kept in seconds, so nothing is
-- lost between a Java Duration and the database. Money is NUMERIC(10, 2).
-- deposit_reference is the gateway's reference of the authorized deposit (BR11): a booking exists
-- only after its deposit is authorized.
CREATE TABLE bookings (
    id                       UUID                     PRIMARY KEY,
    account_id               UUID                     NOT NULL,
    vehicle_id               UUID                     NOT NULL,
    pass_id                  UUID                     NOT NULL,
    start_hub_id             UUID                     NOT NULL,
    planned_start            TIMESTAMP WITH TIME ZONE NOT NULL,
    planned_end              TIMESTAMP WITH TIME ZONE NOT NULL,
    deposit_reference        VARCHAR(100)             NOT NULL,
    deposit_amount           NUMERIC(10, 2)           NOT NULL,
    start_window_seconds     BIGINT                   NOT NULL,
    overtime_grace_seconds   BIGINT                   NOT NULL,
    overtime_block_seconds   BIGINT                   NOT NULL,
    overtime_price_per_block NUMERIC(10, 2)           NOT NULL,
    state                    VARCHAR(20)              NOT NULL,
    started_at               TIMESTAMP WITH TIME ZONE,
    ended_at                 TIMESTAMP WITH TIME ZONE,
    end_hub_id               UUID,
    cancel_reason            VARCHAR(100),
    CONSTRAINT fk_bookings_account FOREIGN KEY (account_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_bookings_pass FOREIGN KEY (pass_id) REFERENCES passes (id),
    CONSTRAINT fk_bookings_start_hub FOREIGN KEY (start_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_bookings_end_hub FOREIGN KEY (end_hub_id) REFERENCES hubs (id),
    CONSTRAINT ck_bookings_period CHECK (planned_end > planned_start),
    CONSTRAINT ck_bookings_deposit CHECK (deposit_amount >= 0 AND LENGTH(TRIM(deposit_reference)) > 0),
    CONSTRAINT ck_bookings_rules CHECK (
        start_window_seconds >= 0 AND overtime_grace_seconds >= 0 AND overtime_block_seconds > 0
            AND overtime_price_per_block >= 0),
    CONSTRAINT ck_bookings_state CHECK (
        state IN ('RESERVED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    -- One rule per state, as in Booking, so no combination of NULLs slips through.
    CONSTRAINT ck_bookings_state_fields CHECK (
        (state IN ('RESERVED', 'EXPIRED') AND started_at IS NULL AND ended_at IS NULL
            AND end_hub_id IS NULL AND cancel_reason IS NULL)
        OR (state = 'ACTIVE' AND started_at IS NOT NULL AND ended_at IS NULL
            AND end_hub_id IS NULL AND cancel_reason IS NULL)
        OR (state = 'COMPLETED' AND started_at IS NOT NULL AND ended_at IS NOT NULL
            AND ended_at >= started_at AND end_hub_id IS NOT NULL AND cancel_reason IS NULL)
        OR (state = 'CANCELLED' AND started_at IS NULL AND ended_at IS NULL
            AND end_hub_id IS NULL AND LENGTH(TRIM(cancel_reason)) > 0))
);

-- BR3: a vehicle has at most one Reserved or Active booking. Safety net under the vehicle lock of
-- the booking use cases (KAN-702).
CREATE UNIQUE INDEX uk_bookings_ongoing_vehicle ON bookings (vehicle_id)
    WHERE state IN ('RESERVED', 'ACTIVE');

-- BR6: a tourist's bookings are searched for overlaps on every new booking, and listed (GET /bookings).
CREATE INDEX ix_bookings_account ON bookings (account_id, planned_start DESC);