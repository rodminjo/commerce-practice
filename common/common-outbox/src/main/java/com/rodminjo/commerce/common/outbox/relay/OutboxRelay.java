package com.rodminjo.commerce.common.outbox.relay;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.inbox.EventIdHeader;
import com.rodminjo.commerce.common.outbox.repository.OutboxRepository;
import com.rodminjo.commerce.common.time.ClockHolder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
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
        ProducerRecord<String, Object> record =
            new ProducerRecord<>(event.getTopic(), event.getPartitionKey(), message);
        // 멱등 컨슈머용 메시지 식별자 전파(outbox UUID). 컨슈머는 EventIdHeader.parse로 dedup 키를 추출.
        record
            .headers()
            .add(
                new RecordHeader(
                    EventIdHeader.HEADER,
                    event.getId().toString().getBytes(StandardCharsets.UTF_8)));
        kafkaTemplate.send(record);
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
