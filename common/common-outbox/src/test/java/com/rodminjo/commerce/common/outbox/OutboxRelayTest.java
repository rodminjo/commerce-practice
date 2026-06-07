package com.rodminjo.commerce.common.outbox;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxTypeRegistry registry;
    private OutboxRelay relay;

    private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        registry = new OutboxTypeRegistry();
        registry.register(StringValue.getDescriptor());
        relay = new OutboxRelay(kafkaTemplate, outboxRepository, registry, () -> FIXED_NOW);
    }

    private OutboxEvent buildPendingEvent() throws Exception {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType("Order");
        event.setAggregateId("oid-1");
        event.setEventType("google.protobuf.StringValue");
        event.setTopic("order.placed");
        event.setPartitionKey("oid-1");
        event.setPayload(StringValue.of("x").toByteArray());
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setCreatedAt(Instant.now());
        return event;
    }

    @Test
    void publishBatch_sendsKafkaMessage_andMarksPublished() throws Exception {
        // given
        OutboxEvent event = buildPendingEvent();
        when(outboxRepository.lockPendingBatch(anyInt())).thenReturn(List.of(event));

        // when
        int count = relay.publishBatch();

        // then
        assertThat(count).isEqualTo(1);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("order.placed");
        assertThat(keyCaptor.getValue()).isEqualTo("oid-1");
        assertThat(valueCaptor.getValue()).isInstanceOf(DynamicMessage.class);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void publishBatch_whenNoPendingEvents_sendsNothing() throws Exception {
        // given
        OutboxEvent event = buildPendingEvent();
        when(outboxRepository.lockPendingBatch(anyInt()))
                .thenReturn(List.of(event))
                .thenReturn(Collections.emptyList());

        // first call publishes 1
        relay.publishBatch();
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());

        // second call finds nothing
        int count = relay.publishBatch();

        assertThat(count).isEqualTo(0);
        // still only 1 total send
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }
}
