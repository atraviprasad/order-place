-- User Service schema (replaces the placeholder V1__init.sql).
-- Passwords, tokens and sessions live in Keycloak, never here.
-- Do not edit after it has been applied anywhere; add V2__... instead.

-- ---------- Customer organisations (e.g. Wells Fargo) ----------
CREATE TABLE customer (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_code     VARCHAR(30)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    contact_person    VARCHAR(150) NOT NULL,
    email             VARCHAR(254) NOT NULL,
    phone             VARCHAR(30)  NOT NULL,
    billing_address   TEXT         NOT NULL,
    delivery_address  TEXT         NOT NULL,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(100) NOT NULL,
    updated_by        VARCHAR(100) NOT NULL,
    CONSTRAINT ck_customer_code_format CHECK (customer_code ~ '^[A-Za-z0-9_-]{2,30}$')
);
CREATE UNIQUE INDEX uq_customer_code      ON customer (UPPER(customer_code));
CREATE UNIQUE INDEX uq_customer_name_mail ON customer (LOWER(name), LOWER(email));
CREATE INDEX idx_customer_active_name     ON customer (is_active, name);

-- ---------- Application users (Customer and Dealer users; Admins exist only in Keycloak) ----------
-- Keycloak enabled flag = app_user.is_active AND the organisation is active.
CREATE TABLE app_user (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id  UUID         NOT NULL,
    email             VARCHAR(254) NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    phone             VARCHAR(30),
    role              VARCHAR(20)  NOT NULL,
    org_id            UUID         NOT NULL,   -- customer.id when role = CUSTOMER; dealer id (Dealer Service) when role = DEALER
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(100) NOT NULL,
    updated_by        VARCHAR(100) NOT NULL,
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER', 'DEALER'))
);
-- No foreign key on org_id: a dealer lives in another service's database. The service validates it instead.
CREATE UNIQUE INDEX uq_app_user_keycloak ON app_user (keycloak_user_id);
CREATE UNIQUE INDEX uq_app_user_email    ON app_user (LOWER(email));
CREATE INDEX idx_app_user_org            ON app_user (org_id);
CREATE INDEX idx_app_user_role_active    ON app_user (role, is_active);
