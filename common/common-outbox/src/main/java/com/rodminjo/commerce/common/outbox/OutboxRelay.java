package com.rodminjo.commerce.common.outbox;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.rodminjo.commerce.common.time.ClockHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxRepository outboxRepository;
    private final OutboxTypeRegistry registry;
    private final ClockHolder clockHolder;

    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    public OutboxRelay(KafkaTemplate<String, Object> kafkaTemplate,
                       OutboxRepository outboxRepository,
                       OutboxTypeRegistry registry,
                       ClockHolder clockHolder) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxRepository = outboxRepository;
        this.registry = registry;
        this.clockHolder = clockHolder;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
    public void poll() {
        publishBatch();
    }

    @Transactional
    public int publishBatch() {
        List<OutboxEvent> pending = outboxRepository.lockPendingBatch(batchSize);
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
                log.warn("Failed to publish outbox event id={} eventType={} attempts={}: {}",
                        event.getId(), event.getEventType(), event.getAttempts(), e.getMessage(), e);
            }
        }
        return published;
    }
}
