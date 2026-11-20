-- Dealer Service schema (replaces the placeholder V1__init.sql).
-- Do not edit after it has been applied anywhere; add V2__... instead.

CREATE TABLE dealer (
                        id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                        dealer_code          VARCHAR(30)  NOT NULL,
                        name                 VARCHAR(200) NOT NULL,
                        contact_person       VARCHAR(150) NOT NULL,
                        email                VARCHAR(254) NOT NULL,
                        phone                VARCHAR(30)  NOT NULL,
                        address              TEXT         NOT NULL,
                        tax_registration_no  VARCHAR(50),
                        is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
                        version              BIGINT       NOT NULL DEFAULT 0,   -- JPA @Version (optimistic locking)
                        created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
                        updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
                        created_by           VARCHAR(100) NOT NULL,             -- Keycloak user id (JWT subject)
                        updated_by           VARCHAR(100) NOT NULL,
                        CONSTRAINT ck_dealer_code_format CHECK (dealer_code ~ '^[A-Za-z0-9_-]{2,30}$')
    );

-- Business rules as constraints (case-insensitive)
CREATE UNIQUE INDEX uq_dealer_code      ON dealer (UPPER(dealer_code));
CREATE UNIQUE INDEX uq_dealer_name_mail ON dealer (LOWER(name), LOWER(email));

-- Customer dropdown: active dealers sorted by name; admin list filter by status
CREATE INDEX idx_dealer_active_name ON dealer (is_active, name);
