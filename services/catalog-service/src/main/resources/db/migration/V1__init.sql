-- Catalog Service schema (database: catalog_db)
CREATE TABLE category (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID REFERENCES category (id),
    name        VARCHAR(120) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100)
);
-- sibling names are unique ignoring case (root level handled by the COALESCE)
CREATE UNIQUE INDEX uq_category_sibling_name
    ON category (COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(name));
CREATE INDEX idx_category_parent ON category (parent_id);

CREATE TABLE product (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku              VARCHAR(40)    NOT NULL,
    name             VARCHAR(200)   NOT NULL,
    description      TEXT,
    category_id      UUID           NOT NULL REFERENCES category (id),
    unit_price       NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    currency         CHAR(3)        NOT NULL DEFAULT 'INR',
    unit_of_measure  VARCHAR(20)    NOT NULL DEFAULT 'EACH',
    availability     VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE'
                     CHECK (availability IN ('AVAILABLE', 'OUT_OF_STOCK')),
    min_order_qty    INT            NOT NULL DEFAULT 1 CHECK (min_order_qty >= 1),
    attributes       JSONB          NOT NULL DEFAULT '{}'::jsonb,
    is_active        BOOLEAN        NOT NULL DEFAULT TRUE,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100)
);
CREATE UNIQUE INDEX uq_product_sku ON product (UPPER(sku));
CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_product_active_name ON product (is_active, LOWER(name));

CREATE TABLE product_image (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   UUID         NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    storage_key  VARCHAR(300) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    is_primary   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order   INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(100)
);
CREATE INDEX idx_product_image_product ON product_image (product_id);
-- at most one primary image per product
CREATE UNIQUE INDEX uq_product_image_primary ON product_image (product_id) WHERE is_primary;

CREATE TABLE product_price_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID           NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    old_price   NUMERIC(12, 2) NOT NULL,
    new_price   NUMERIC(12, 2) NOT NULL,
    changed_by  VARCHAR(100),
    changed_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_price_history_product ON product_price_history (product_id, changed_at DESC);
