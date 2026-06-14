SET search_path TO payment_svc;

-- 환불 금액의 통화. 환불은 주문/결제와 별개 애그리거트로, 금액(Money)을 자체 보유한다.
-- 통화는 결제 통화에서 파생되지만 환불 레코드가 자기 기술적으로 완결되도록 컬럼으로 저장한다.
ALTER TABLE refunds ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'KRW';
