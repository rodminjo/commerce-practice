CREATE SCHEMA IF NOT EXISTS order_svc;
SET search_path TO order_svc;

CREATE TABLE orders (
    id                 UUID         PRIMARY KEY,
    customer_id        VARCHAR(64)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    total_amount_minor BIGINT       NOT NULL,
    currency           VARCHAR(3)   NOT NULL,
    -- Business field: order creation time, owned by the domain (Order.place()).
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Audit columns (BaseEntity / JPA auditing), kept separate via the audit_ prefix.
    -- *_by default to SYSTEM for non-application writes (seed/manual); the app supplies the JWT subject.
    audit_created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_created_by   VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    audit_updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_updated_by   VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE order_items (
    id               BIGSERIAL    PRIMARY KEY,
    order_id         UUID         NOT NULL,
    product_id       VARCHAR(64)  NOT NULL,
    quantity         INT          NOT NULL,
    unit_price_minor BIGINT       NOT NULL,
    -- Audit columns (BaseEntity / JPA auditing). No business creation time (OrderLineItem has none).
    audit_created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_created_by VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    audit_updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_updated_by VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM'
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

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
