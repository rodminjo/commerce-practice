package com.rodminjo.commerce.common.outbox.appender;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.repository.OutboxRepository;
import com.rodminjo.commerce.common.time.ClockHolder;
import java.util.UUID;

/**
 * JPA-backed {@link OutboxAppender}. Carries no own {@code @Transactional} — it joins the caller's
 * transaction (default propagation) so the order INSERT and the outbox INSERT commit together.
 * Wired as a {@code @Bean} in {@link com.rodminjo.commerce.common.outbox.OutboxAutoConfiguration}
 * (not component-scanned).
 */
public class JpaOutboxAppender implements OutboxAppender {

  private final OutboxRepository outboxRepository;
  private final ClockHolder clockHolder;

  public JpaOutboxAppender(OutboxRepository outboxRepository, ClockHolder clockHolder) {
    this.outboxRepository = outboxRepository;
    this.clockHolder = clockHolder;
  }

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
