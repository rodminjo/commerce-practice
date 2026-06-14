SET search_path TO order_svc;

-- 멱등 컨슈머(Inbox) dedup 레코드. (consumer_group, event_id) 복합 PK로 컨슈머 그룹당 메시지 1회 처리 보장.
CREATE TABLE processed_event (
    consumer_group VARCHAR(64)  NOT NULL,
    event_id       UUID         NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (consumer_group, event_id)
);
