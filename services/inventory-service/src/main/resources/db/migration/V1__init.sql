CREATE SCHEMA IF NOT EXISTS inventory_svc;
SET search_path TO inventory_svc;

CREATE TABLE inventory (
    product_id       VARCHAR(64)  PRIMARY KEY,
    stock            INT          NOT NULL,
    reserved         INT          NOT NULL DEFAULT 0,
    -- Audit columns (BaseEntity / JPA auditing).
    audit_created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_created_by VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    audit_updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_updated_by VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    -- Second line of defense behind the atomic conditional UPDATE: the DB itself rejects any
    -- write that would make reserved negative or exceed stock (oversell = 0, belt and suspenders).
    CONSTRAINT chk_reserved_nonneg    CHECK (reserved >= 0),
    CONSTRAINT chk_reserved_le_stock  CHECK (reserved <= stock)
);

-- Seed: prod-1 plentiful, prod-2 scarce (oversell drills target prod-2).
INSERT INTO inventory (product_id, stock, reserved) VALUES
    ('prod-1', 100, 0),
    ('prod-2', 5,   0);

CREATE TABLE inventory_reservation (
    id               BIGSERIAL    PRIMARY KEY,
    order_id         VARCHAR(64)  NOT NULL,
    product_id       VARCHAR(64)  NOT NULL,
    quantity         INT          NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    audit_created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_created_by VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    audit_updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_updated_by VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM'
);

CREATE INDEX idx_reservation_order_status ON inventory_reservation(order_id, status);

CREATE TABLE outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(128) NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    partition_key  VARCHAR(128) NOT NULL,
    payload        BYTEA        NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status_created ON outbox(status, created_at);
