CREATE SCHEMA IF NOT EXISTS payment_svc;
SET search_path TO payment_svc;

CREATE TABLE payments (
    id               UUID         PRIMARY KEY,
    order_id         VARCHAR(64)  NOT NULL,
    amount_minor     BIGINT       NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    -- Audit columns (BaseEntity / JPA auditing).
    audit_created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_created_by VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    audit_updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    audit_updated_by VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    -- Foundation for Week 4 idempotent consumers: the key is unique now, dedup logic lands later.
    CONSTRAINT uq_payments_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_order_id ON payments(order_id);

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
