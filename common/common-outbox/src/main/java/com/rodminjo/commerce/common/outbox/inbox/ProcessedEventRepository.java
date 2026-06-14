package com.rodminjo.commerce.common.outbox.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * inbox 영속성 인터페이스. {@link IdempotentConsumer}가 dedup 행을 INSERT(saveAndFlush)하여 중복 처리를 차단하는 용도로만 사용.
 * 복합키 타입은 {@link ProcessedEventId}.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {}
