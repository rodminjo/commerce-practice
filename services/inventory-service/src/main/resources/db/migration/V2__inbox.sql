SET search_path TO inventory_svc;

-- 멱등 컨슈머 dedup 테이블. (consumerGroup, eventId) 복합 PK로 중복 메시지 차단.
-- 서비스가 처리에 성공한 경우에만 행이 커밋된다(핸들러 예외 시 함께 롤백).
CREATE TABLE processed_event (
  consumer_group VARCHAR(64)  NOT NULL,
  event_id       UUID         NOT NULL,
  processed_at   TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (consumer_group, event_id)
);
