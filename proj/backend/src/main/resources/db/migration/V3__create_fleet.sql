-- US08 (KAN-60): hubs, vehicles and the vehicle state history.
-- Codes follow the identifier format of the telemetry contract (FleetCode), so they match the
-- simulator's hub and vehicle ids.

CREATE TABLE hubs (
    id        UUID             PRIMARY KEY,
    code      VARCHAR(64)      NOT NULL,
    name      VARCHAR(100)     NOT NULL,
    latitude  DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    capacity  INTEGER          NOT NULL,
    CONSTRAINT uk_hubs_code UNIQUE (code),
    CONSTRAINT ck_hubs_capacity CHECK (capacity >= 1),
    CONSTRAINT ck_hubs_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_hubs_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE vehicles (
    id     UUID        PRIMARY KEY,
    code   VARCHAR(64) NOT NULL,
    type   VARCHAR(20) NOT NULL,
    state  VARCHAR(20) NOT NULL,
    -- The hub the vehicle is at; NULL while InUse, InTransit or Retired.
    hub_id UUID,
    CONSTRAINT uk_vehicles_code UNIQUE (code),
    CONSTRAINT fk_vehicles_hub FOREIGN KEY (hub_id) REFERENCES hubs (id),
    CONSTRAINT ck_vehicles_type CHECK (type IN ('E_BIKE', 'E_SCOOTER')),
    CONSTRAINT ck_vehicles_state CHECK (state IN ('DEACTIVATED', 'AVAILABLE', 'RESERVED', 'IN_USE',
                                                  'MAINTENANCE', 'IN_TRANSIT', 'RETIRED')),
    -- BR14: at a hub if and only if the state is one physically at a hub (VehicleState.isAtHub).
    CONSTRAINT ck_vehicles_hub_matches_state CHECK (
        (hub_id IS NOT NULL) = (state IN ('DEACTIVATED', 'AVAILABLE', 'RESERVED', 'MAINTENANCE')))
);

-- countAtHub runs on every registration and arrival (BR14).
CREATE INDEX ix_vehicles_hub_id ON vehicles (hub_id);

-- BR16: every allowed transition, in order. Only inserted, never updated. from_state is NULL for
-- the registration. The identity id keeps the order of changes made at the same instant.
CREATE TABLE vehicle_state_changes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vehicle_id UUID                     NOT NULL,
    from_state VARCHAR(20),
    to_state   VARCHAR(20)              NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_vehicle_state_changes_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT ck_vehicle_state_changes_from CHECK (from_state IN ('DEACTIVATED', 'AVAILABLE',
        'RESERVED', 'IN_USE', 'MAINTENANCE', 'IN_TRANSIT', 'RETIRED')),
    CONSTRAINT ck_vehicle_state_changes_to CHECK (to_state IN ('DEACTIVATED', 'AVAILABLE',
        'RESERVED', 'IN_USE', 'MAINTENANCE', 'IN_TRANSIT', 'RETIRED')),
    CONSTRAINT ck_vehicle_state_changes_differ CHECK (from_state IS DISTINCT FROM to_state)
);

CREATE INDEX ix_vehicle_state_changes_vehicle ON vehicle_state_changes (vehicle_id, id);
