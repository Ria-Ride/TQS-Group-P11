-- US06 (KAN-710 to KAN-712): ending a ride.
-- A booking keeps the repositioning window of the rules it was created with (BR21), and the moment
-- of its first refused end at a full hub (BR15). Bookings created before this migration get the
-- window of version 1 of the rules of use, the only version that existed for them by default.
ALTER TABLE bookings
    ADD COLUMN repositioning_window_seconds BIGINT,
    ADD COLUMN full_hub_attempt_at          TIMESTAMP WITH TIME ZONE;

UPDATE bookings
SET repositioning_window_seconds =
        (SELECT repositioning_window_minutes * 60 FROM rules_of_use WHERE version = 1);

ALTER TABLE bookings
    ALTER COLUMN repositioning_window_seconds SET NOT NULL,
    ADD CONSTRAINT ck_bookings_repositioning CHECK (repositioning_window_seconds >= 0),
    -- As in Booking: only a ride that started can have been refused at a full hub.
    ADD CONSTRAINT ck_bookings_full_hub_attempt CHECK (
        full_hub_attempt_at IS NULL OR state IN ('ACTIVE', 'COMPLETED'));

-- BR13: a deposit can now be captured, keeping how much of it was taken (more than 0, at most the
-- deposit); the gateway gave the rest back in the same step. Only a captured deposit has it.
ALTER TABLE payments
    ADD COLUMN captured_amount NUMERIC(10, 2);

ALTER TABLE payments
    DROP CONSTRAINT ck_payments_state_fields;

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_state_fields CHECK (
        (kind = 'PASS' AND booking_id IS NULL AND captured_amount IS NULL AND (
            (state = 'CAPTURED' AND pass_id IS NOT NULL AND gateway_reference IS NOT NULL
                AND failure_reason IS NULL)
            OR (state = 'DECLINED' AND pass_id IS NULL AND failure_reason IS NOT NULL)))
        OR (kind = 'DEPOSIT' AND pass_id IS NULL AND (
            (state = 'AUTHORIZED' AND booking_id IS NOT NULL AND gateway_reference IS NOT NULL
                AND failure_reason IS NULL AND captured_amount IS NULL)
            OR (state = 'CAPTURED' AND booking_id IS NOT NULL AND gateway_reference IS NOT NULL
                AND failure_reason IS NULL AND captured_amount > 0 AND captured_amount <= amount)
            OR (state = 'RELEASED' AND gateway_reference IS NOT NULL AND failure_reason IS NULL
                AND captured_amount IS NULL)
            OR (state = 'DECLINED' AND booking_id IS NULL AND failure_reason IS NOT NULL
                AND captured_amount IS NULL))));
