-- US09 (KAN-61): the last telemetry reading of a vehicle. All NULL until the first reading.
-- last_reading_at is the timestamp of the last reading applied: a reading is applied only if its
-- timestamp is newer (BR17). A position is both coordinates or none; it stays NULL while only the
-- hub of the vehicle is known.
ALTER TABLE vehicles
    ADD COLUMN lock_state      VARCHAR(10),
    ADD COLUMN latitude        DOUBLE PRECISION,
    ADD COLUMN longitude       DOUBLE PRECISION,
    ADD COLUMN last_reading_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT ck_vehicles_lock_state CHECK (lock_state IN ('LOCKED', 'UNLOCKED')),
    ADD CONSTRAINT ck_vehicles_latitude CHECK (latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_vehicles_longitude CHECK (longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_vehicles_position_both_or_none CHECK ((latitude IS NULL) = (longitude IS NULL));
