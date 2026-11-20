-- Order Service schema (database: order_db)
-- The table is called "orders" because ORDER is a reserved word in SQL.

CREATE SEQUENCE order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE orders (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number            VARCHAR(30),                      -- assigned at submit, e.g. OP-2026-000123
    status                  VARCHAR(30)    NOT NULL
        CHECK (status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','AWAITING_PAYMENT','PAID',
                          'READY_FOR_DELIVERY','REJECTED','CANCELLED')),
    customer_id             UUID           NOT NULL,          -- no FK: lives in user_db
    customer_name_snapshot  VARCHAR(200)   NOT NULL,
    customer_email_snapshot VARCHAR(254),
    dealer_id               UUID,                             -- chosen in the draft, required at submit
    dealer_name_snapshot    VARCHAR(200),
    dealer_email_snapshot   VARCHAR(254),
    created_by_user_id      UUID           NOT NULL,
    delivery_address        TEXT,
    customer_notes          TEXT,
    subtotal                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_amount              NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    currency                CHAR(3)        NOT NULL DEFAULT 'INR',
    submit_idempotency_key  VARCHAR(100),
    submitted_at            TIMESTAMPTZ,
    payment_due_at          TIMESTAMPTZ,                      -- reserved, time rules are skipped for now
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_orders_order_number ON orders (order_number) WHERE order_number IS NOT NULL;
CREATE INDEX idx_orders_customer ON orders (customer_id, status, submitted_at DESC);
CREATE INDEX idx_orders_dealer   ON orders (dealer_id, status, submitted_at DESC);
CREATE INDEX idx_orders_status   ON orders (status, created_at DESC);

CREATE TABLE order_line (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id               UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    line_no                INT            NOT NULL,
    product_id             UUID           NOT NULL,           -- no FK: lives in catalog_db
    sku_snapshot           VARCHAR(40)    NOT NULL,
    product_name_snapshot  VARCHAR(200)   NOT NULL,
    unit_price_snapshot    NUMERIC(12, 2) NOT NULL CHECK (unit_price_snapshot >= 0),
    submitted_qty          INT            NOT NULL CHECK (submitted_qty >= 1),  -- never changes after submit
    approved_qty           INT            CHECK (approved_qty >= 0),            -- set by the dealer, 0 = removed
    line_total             NUMERIC(14, 2) NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_order_line_product ON order_line (order_id, product_id);
CREATE INDEX idx_order_line_order ON order_line (order_id, line_no);

CREATE TABLE order_status_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status         VARCHAR(30),
    to_status           VARCHAR(30) NOT NULL,
    changed_by_user_id  UUID,                                  -- null when the system changed it
    changed_by_role     VARCHAR(20) NOT NULL,                  -- CUSTOMER, DEALER, ADMIN, SYSTEM
    reason              TEXT,
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_status_history_order ON order_status_history (order_id, changed_at);

CREATE TABLE dealer_action (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    action          VARCHAR(10) NOT NULL CHECK (action IN ('APPROVE','REJECT','EDIT')),
    dealer_user_id  UUID        NOT NULL,
    remarks         TEXT,
    acted_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dealer_action_order ON dealer_action (order_id);

-- Transactional outbox: written in the same transaction as the order change, published to Kafka afterwards.
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,                           -- same as the event id in the envelope
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,                      -- the full event envelope
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

-- Events already handled (idempotent consumers; used from the Payment step)
CREATE TABLE processed_event (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
