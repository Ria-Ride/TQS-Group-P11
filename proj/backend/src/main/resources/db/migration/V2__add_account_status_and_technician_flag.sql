-- US13 (KAN-77): accounts can be deactivated, and an OPERATOR can be a field technician (BR20).
-- Existing accounts stay active and are not technicians.
ALTER TABLE users
    ADD COLUMN field_technician BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN active           BOOLEAN NOT NULL DEFAULT TRUE,
    -- BR20 also for rows edited by hand (admin roles are managed directly in the database).
    ADD CONSTRAINT ck_users_field_technician_is_operator
        CHECK (NOT field_technician OR role = 'OPERATOR');