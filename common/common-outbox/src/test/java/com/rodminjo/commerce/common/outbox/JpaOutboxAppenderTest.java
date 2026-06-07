package com.rodminjo.commerce.common.outbox;

import com.google.protobuf.StringValue;
import com.rodminjo.commerce.common.time.ClockHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaOutboxAppenderTest {

    private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ClockHolder clockHolder;

    @InjectMocks
    private JpaOutboxAppender appender;

    @Test
    void append_savesOutboxEventWithCorrectFields() {
        // given
        when(clockHolder.now()).thenReturn(FIXED_NOW);
        StringValue message = StringValue.of("hello");

        // when
        appender.append("Order", "oid-1", "order.placed", "oid-1", message);

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Order");
        assertThat(saved.getAggregateId()).isEqualTo("oid-1");
        assertThat(saved.getTopic()).isEqualTo("order.placed");
        assertThat(saved.getPartitionKey()).isEqualTo("oid-1");
        assertThat(saved.getEventType()).isEqualTo("google.protobuf.StringValue");
        assertThat(saved.getPayload()).isEqualTo(message.toByteArray());
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isEqualTo(0);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
    }
}
