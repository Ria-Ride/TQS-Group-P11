-- US01 (KAN-53): user accounts. E-mails are stored normalized (trimmed, lower case).
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('END_USER', 'OPERATOR', 'CLIENT_MANAGER', 'ADMIN'))
);