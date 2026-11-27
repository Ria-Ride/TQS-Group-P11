-- US04 (KAN-701): payments also keep the deposits of bookings. kind says what a payment is for;
-- booking_id is the booking a deposit belongs to. The rules per kind and state repeat those of
-- Payment: a held deposit always has its booking (BR11), a declined one never; one deposit per
-- booking. Existing rows are passes (US03).
ALTER TABLE payments
    ADD COLUMN kind       VARCHAR(20),
    ADD COLUMN booking_id UUID;

UPDATE payments SET kind = 'PASS';

ALTER TABLE payments
    DROP CONSTRAINT ck_payments_state,
    DROP CONSTRAINT ck_payments_state_fields;

ALTER TABLE payments
    ALTER COLUMN kind SET NOT NULL,
    ADD CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    ADD CONSTRAINT uk_payments_booking UNIQUE (booking_id),
    ADD CONSTRAINT ck_payments_kind CHECK (kind IN ('PASS', 'DEPOSIT')),
    ADD CONSTRAINT ck_payments_state CHECK (
        state IN ('AUTHORIZED', 'CAPTURED', 'RELEASED', 'DECLINED')),
    -- One rule per kind and state, so no combination of NULLs slips through.
    ADD CONSTRAINT ck_payments_state_fields CHECK (
        (kind = 'PASS' AND booking_id IS NULL AND (
            (state = 'CAPTURED' AND pass_id IS NOT NULL AND gateway_reference IS NOT NULL
                AND failure_reason IS NULL)
            OR (state = 'DECLINED' AND pass_id IS NULL AND failure_reason IS NOT NULL)))
        OR (kind = 'DEPOSIT' AND pass_id IS NULL AND (
            (state = 'AUTHORIZED' AND booking_id IS NOT NULL AND gateway_reference IS NOT NULL
                AND failure_reason IS NULL)
            OR (state = 'RELEASED' AND gateway_reference IS NOT NULL AND failure_reason IS NULL)
            OR (state = 'DECLINED' AND booking_id IS NULL AND failure_reason IS NOT NULL))));