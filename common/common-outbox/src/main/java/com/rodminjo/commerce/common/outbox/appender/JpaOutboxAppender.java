package com.rodminjo.commerce.common.outbox.appender;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.repository.OutboxRepository;
import com.rodminjo.commerce.common.time.ClockHolder;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * JPA 기반 {@link OutboxAppender}. 자체 {@code @Transactional} 없음. 호출자 트랜잭션에 합류(기본 전파)하여 주문 INSERT와
 * 아웃박스 INSERT를 원자적으로 커밋. {@link com.rodminjo.commerce.common.outbox.OutboxAutoConfiguration}에서
 * {@code @Bean}으로 등록(컴포넌트 스캔 제외).
 */
@RequiredArgsConstructor
public class JpaOutboxAppender implements OutboxAppender {

  private final OutboxRepository outboxRepository;
  private final ClockHolder clockHolder;

  @Override
  public void append(
      String aggregateType, String aggregateId, String topic, String partitionKey, Message event) {
    OutboxEvent outboxEvent =
        OutboxEvent.pending(
            UUID.randomUUID(),
            aggregateType,
            aggregateId,
            event.getDescriptorForType().getFullName(),
            topic,
            partitionKey,
            event.toByteArray(),
            clockHolder.now());
    outboxRepository.save(outboxEvent);
  }
}
