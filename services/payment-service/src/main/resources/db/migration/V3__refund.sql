SET search_path TO payment_svc;

-- 환불 레코드. idempotency_key(=refund_id) UNIQUE로 같은 환불 요청 재처리 시 이중 환불 차단.
CREATE TABLE refunds (
    refund_id       VARCHAR(64)  PRIMARY KEY,
    payment_id      VARCHAR(64)  NOT NULL,
    order_id        VARCHAR(64)  NOT NULL,
    amount_minor    BIGINT       NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_refund_idem UNIQUE (idempotency_key)
);

-- 부분/전체 환불 누적액. (refunded_amount_minor + amount <= amount_minor) 가드로 초과 환불 거부.
ALTER TABLE payments ADD COLUMN refunded_amount_minor BIGINT NOT NULL DEFAULT 0;
