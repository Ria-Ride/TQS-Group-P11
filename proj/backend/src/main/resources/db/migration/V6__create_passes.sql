-- US03 (KAN-98): pass types, partner hotels and their stays, and the passes tourists buy.
-- Money is NUMERIC(10, 2): euros with cents, never a floating point number.

-- A hotel of the client's group whose guests get a discount on passes (BR9).
CREATE TABLE partner_accommodations (
    id               UUID         PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    discount_percent INTEGER      NOT NULL,
    CONSTRAINT uk_partner_accommodations_name UNIQUE (name),
    CONSTRAINT ck_partner_accommodations_discount CHECK (discount_percent BETWEEN 0 AND 100)
);

-- A guest's stay, identified by the code the hotel gives the guest. Codes are stored upper-case so
-- that a code typed in lower case is found. BR9: valid from check_in to check_out, both included;
-- redeemed_by is the account the code is linked to, NULL until a pass is bought with it.
CREATE TABLE stays (
    id               UUID        PRIMARY KEY,
    code             VARCHAR(32) NOT NULL,
    accommodation_id UUID        NOT NULL,
    check_in         DATE        NOT NULL,
    check_out        DATE        NOT NULL,
    redeemed_by      UUID,
    CONSTRAINT uk_stays_code UNIQUE (code),
    CONSTRAINT fk_stays_accommodation FOREIGN KEY (accommodation_id)
        REFERENCES partner_accommodations (id),
    CONSTRAINT fk_stays_redeemed_by FOREIGN KEY (redeemed_by) REFERENCES users (id),
    CONSTRAINT ck_stays_code_upper_case CHECK (code = UPPER(code) AND LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_stays_dates CHECK (check_out >= check_in)
);

-- A kind of pass (BR8): validity in whole days of 24 hours, and its price before any discount.
CREATE TABLE pass_types (
    id            UUID          PRIMARY KEY,
    code          VARCHAR(32)   NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    validity_days INTEGER       NOT NULL,
    base_price    NUMERIC(10, 2) NOT NULL,
    CONSTRAINT uk_pass_types_code UNIQUE (code),
    CONSTRAINT ck_pass_types_validity CHECK (validity_days >= 1),
    CONSTRAINT ck_pass_types_price CHECK (base_price >= 0)
);

-- The vehicle types a pass type allows (at least one, checked by the domain).
CREATE TABLE pass_type_vehicle_types (
    pass_type_id UUID        NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    CONSTRAINT pk_pass_type_vehicle_types PRIMARY KEY (pass_type_id, vehicle_type),
    CONSTRAINT fk_pass_type_vehicle_types_pass_type FOREIGN KEY (pass_type_id)
        REFERENCES pass_types (id),
    CONSTRAINT ck_pass_type_vehicle_types_type CHECK (vehicle_type IN ('E_BIKE', 'E_SCOOTER'))
);

-- A pass an account bought. It exists only after an approved payment (BR10). price is what was
-- paid, after any stay discount (BR9); stay_id is the stay whose code gave it, or NULL.
CREATE TABLE passes (
    id           UUID                     PRIMARY KEY,
    account_id   UUID                     NOT NULL,
    pass_type_id UUID                     NOT NULL,
    stay_id      UUID,
    price        NUMERIC(10, 2)           NOT NULL,
    valid_from   TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_to     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_passes_account FOREIGN KEY (account_id) REFERENCES users (id),
    CONSTRAINT fk_passes_pass_type FOREIGN KEY (pass_type_id) REFERENCES pass_types (id),
    CONSTRAINT fk_passes_stay FOREIGN KEY (stay_id) REFERENCES stays (id),
    CONSTRAINT ck_passes_price CHECK (price >= 0),
    CONSTRAINT ck_passes_validity CHECK (valid_to > valid_from)
);

-- An account's passes are listed (GET /passes) and checked by bookings (US04).
CREATE INDEX ix_passes_account ON passes (account_id, valid_from DESC);
