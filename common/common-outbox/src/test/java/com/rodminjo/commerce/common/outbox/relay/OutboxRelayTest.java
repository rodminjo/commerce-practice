package com.rodminjo.commerce.common.outbox.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.StringValue;
import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.entity.OutboxStatus;
import com.rodminjo.commerce.common.outbox.inbox.EventIdHeader;
import com.rodminjo.commerce.common.outbox.repository.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("OutboxRelay")
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @Mock private OutboxRepository outboxRepository;

  private OutboxTypeRegistry registry;
  private OutboxRelay relay;

  private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");

  @BeforeEach
  void setUp() {
    registry = new OutboxTypeRegistry();
    registry.register(StringValue.getDescriptor());
    relay =
        new OutboxRelay(
            kafkaTemplate,
            outboxRepository,
            registry,
            () -> FIXED_NOW,
            new OutboxRelayProperties());
  }

  private OutboxEvent buildPendingEvent(UUID id) throws Exception {
    return OutboxEvent.pending(
        id,
        "Order",
        "oid-1",
        "google.protobuf.StringValue",
        "order.placed",
        "oid-1",
        StringValue.of("x").toByteArray(),
        Instant.now());
  }

  @Nested
  @DisplayName("publishBatch 호출 시")
  class PublishBatch {

    @Test
    @DisplayName("PENDING 이벤트 존재 → topic/key/value + x-event-id 헤더로 전송 후 PUBLISHED 상태로 변경")
    void publishBatch_sendsKafkaMessage_andMarksPublished() throws Exception {
      // given
      UUID eventId = UUID.randomUUID();
      OutboxEvent event = buildPendingEvent(eventId);
      when(outboxRepository.lockPendingBatch(anyInt())).thenReturn(List.of(event));

      // when
      int count = relay.publishBatch();

      // then
      assertThat(count).isEqualTo(1);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor =
          ArgumentCaptor.forClass(ProducerRecord.class);
      verify(kafkaTemplate).send(recordCaptor.capture());

      ProducerRecord<String, Object> record = recordCaptor.getValue();
      assertThat(record.topic()).isEqualTo("order.placed");
      assertThat(record.key()).isEqualTo("oid-1");
      assertThat(record.value()).isInstanceOf(DynamicMessage.class);

      // x-event-id 헤더 = outbox UUID toString UTF-8
      Header header = record.headers().lastHeader(EventIdHeader.HEADER);
      assertThat(header).isNotNull();
      assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo(eventId.toString());

      assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
      assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING 이벤트 없음 → Kafka 전송 없이 0 반환")
    void publishBatch_whenNoPendingEvents_sendsNothing() throws Exception {
      // given
      OutboxEvent event = buildPendingEvent(UUID.randomUUID());
      when(outboxRepository.lockPendingBatch(anyInt()))
          .thenReturn(List.of(event))
          .thenReturn(Collections.emptyList());

      // 첫 번째 호출: 1건 발행
      relay.publishBatch();
      verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));

      // 두 번째 호출: 대상 없음
      int count = relay.publishBatch();

      assertThat(count).isEqualTo(0);
      // 총 전송 횟수는 여전히 1
      verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }
  }
}
