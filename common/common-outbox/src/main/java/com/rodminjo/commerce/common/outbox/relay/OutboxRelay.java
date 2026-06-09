package com.rodminjo.commerce.common.outbox.relay;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.repository.OutboxRepository;
import com.rodminjo.commerce.common.time.ClockHolder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final OutboxRepository outboxRepository;
  private final OutboxTypeRegistry registry;
  private final ClockHolder clockHolder;
  private final OutboxRelayProperties properties;

  @Transactional
  public int publishBatch() {
    List<OutboxEvent> pending = outboxRepository.lockPendingBatch(properties.getBatchSize());
    int published = 0;
    for (OutboxEvent event : pending) {
      try {
        Descriptor descriptor = registry.get(event.getEventType());
        DynamicMessage message = DynamicMessage.parseFrom(descriptor, event.getPayload());
        kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), message);
        event.markPublished(clockHolder.now());
        published++;
      } catch (Exception e) {
        event.incrementAttempts();
        log.warn(
            "Failed to publish outbox event id={} eventType={} attempts={}: {}",
            event.getId(),
            event.getEventType(),
            event.getAttempts(),
            e.getMessage(),
            e);
      }
    }
    return published;
  }
}
