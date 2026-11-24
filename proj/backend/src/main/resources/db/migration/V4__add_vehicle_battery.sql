-- US02 (KAN-54): the last battery level reported for a vehicle, as a percentage.
-- NULL until the first telemetry report: such a vehicle is not offered for booking (BR1).
ALTER TABLE vehicles
    ADD COLUMN battery_percent INTEGER,
    ADD CONSTRAINT ck_vehicles_battery_percent CHECK (battery_percent BETWEEN 0 AND 100);
