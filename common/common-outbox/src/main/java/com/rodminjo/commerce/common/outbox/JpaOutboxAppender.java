package com.rodminjo.commerce.common.outbox;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.time.ClockHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JpaOutboxAppender implements OutboxAppender {

    private final OutboxRepository outboxRepository;
    private final ClockHolder clockHolder;

    public JpaOutboxAppender(OutboxRepository outboxRepository, ClockHolder clockHolder) {
        this.outboxRepository = outboxRepository;
        this.clockHolder = clockHolder;
    }

    @Override
    public void append(String aggregateType, String aggregateId, String topic, String partitionKey, Message event) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setAggregateType(aggregateType);
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setTopic(topic);
        outboxEvent.setPartitionKey(partitionKey);
        outboxEvent.setEventType(event.getDescriptorForType().getFullName());
        outboxEvent.setPayload(event.toByteArray());
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttempts(0);
        outboxEvent.setCreatedAt(clockHolder.now());
        outboxRepository.save(outboxEvent);
    }
}
