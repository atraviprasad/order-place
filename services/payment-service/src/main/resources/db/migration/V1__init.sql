-- Payment Service schema (database: payment_db)
-- Sandbox only. No card data of any kind is stored: only references returned by the gateway.

CREATE TABLE payment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID           NOT NULL,              -- no FK: lives in order_db
    order_number        VARCHAR(30)    NOT NULL,
    customer_id         UUID           NOT NULL,              -- the customer organisation (from the token)
    payer_user_id       UUID           NOT NULL,
    payer_email         VARCHAR(254),                         -- who to tell about the result
    amount              NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    currency            CHAR(3)        NOT NULL DEFAULT 'INR',
    status              VARCHAR(30)    NOT NULL
        CHECK (status IN ('INITIATED','PENDING_CONFIRMATION','SUCCESS','FAILED','EXPIRED')),
    gateway_name        VARCHAR(30)    NOT NULL DEFAULT 'RAZORPAY',
    gateway_order_ref   VARCHAR(60)    NOT NULL,              -- Razorpay order id (order_...)
    gateway_payment_ref VARCHAR(60),                          -- Razorpay payment id (pay_...)
    idempotency_key     VARCHAR(100)   NOT NULL,
    failure_reason      TEXT,
    initiated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_payment_gateway_order ON payment (gateway_name, gateway_order_ref);
CREATE UNIQUE INDEX uq_payment_idempotency   ON payment (idempotency_key);
CREATE INDEX idx_payment_order    ON payment (order_id, initiated_at DESC);
CREATE INDEX idx_payment_customer ON payment (customer_id, initiated_at DESC);
CREATE INDEX idx_payment_status   ON payment (status, initiated_at DESC);
-- an order can be paid only once (FR-PAY-11)
CREATE UNIQUE INDEX uq_payment_one_success_per_order ON payment (order_id) WHERE status = 'SUCCESS';
-- at most one payment in progress per order: a second "pay" click returns the first one
CREATE UNIQUE INDEX uq_payment_one_open_per_order ON payment (order_id)
    WHERE status IN ('INITIATED', 'PENDING_CONFIRMATION');

-- Every webhook call we accepted (valid signature), kept as the audit trail and for duplicate detection.
CREATE TABLE payment_event (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id         UUID REFERENCES payment (id),          -- null when the gateway order is unknown to us
    gateway_event_id   VARCHAR(100) NOT NULL,                 -- header X-Razorpay-Event-Id
    event_type         VARCHAR(60)  NOT NULL,                 -- payment.captured, payment.failed, order.paid, ...
    raw_payload        JSONB        NOT NULL,
    processing_result  VARCHAR(30)  NOT NULL
        CHECK (processing_result IN ('PROCESSED','IGNORED','UNMATCHED','AMOUNT_MISMATCH','DUPLICATE_SUCCESS')),
    received_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_payment_event_gateway_event ON payment_event (gateway_event_id);   -- webhook idempotency (FR-PAY-08)
CREATE INDEX idx_payment_event_payment ON payment_event (payment_id, received_at);

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
