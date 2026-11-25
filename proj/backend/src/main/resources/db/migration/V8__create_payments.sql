-- US03 (KAN-97): every attempt to charge an account, approved or not, so each purchase can be
-- traced. No card data: only the gateway's reference. BR10: a CAPTURED payment has its pass and
-- reference; a DECLINED one (refused, or no answer in time) has a reason and no pass.
-- Deposits (US04) add a kind column and the AUTHORIZED and RELEASED states.
CREATE TABLE payments (
    id                UUID                     PRIMARY KEY,
    account_id        UUID                     NOT NULL,
    pass_id           UUID,
    amount            NUMERIC(10, 2)           NOT NULL,
    state             VARCHAR(20)              NOT NULL,
    gateway_reference VARCHAR(100),
    failure_reason    VARCHAR(50),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_payments_account FOREIGN KEY (account_id) REFERENCES users (id),
    CONSTRAINT fk_payments_pass FOREIGN KEY (pass_id) REFERENCES passes (id),
    CONSTRAINT uk_payments_pass UNIQUE (pass_id),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    CONSTRAINT ck_payments_state CHECK (state IN ('CAPTURED', 'DECLINED')),
    -- One rule per state, so no combination of NULLs slips through.
    CONSTRAINT ck_payments_state_fields CHECK (
        (state = 'CAPTURED' AND pass_id IS NOT NULL AND gateway_reference IS NOT NULL
            AND failure_reason IS NULL)
        OR (state = 'DECLINED' AND pass_id IS NULL AND failure_reason IS NOT NULL))
);

CREATE INDEX ix_payments_account ON payments (account_id, created_at DESC);
