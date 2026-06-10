package com.rodminjo.commerce.common.outbox.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.StringValue;
import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.entity.OutboxStatus;
import com.rodminjo.commerce.common.outbox.repository.OutboxRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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

  private OutboxEvent buildPendingEvent() throws Exception {
    return OutboxEvent.pending(
        UUID.randomUUID(),
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
    @DisplayName("PENDING 이벤트 존재 → Kafka 전송 후 PUBLISHED 상태로 변경")
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
    @DisplayName("PENDING 이벤트 없음 → Kafka 전송 없이 0 반환")
    void publishBatch_whenNoPendingEvents_sendsNothing() throws Exception {
      // given
      OutboxEvent event = buildPendingEvent();
      when(outboxRepository.lockPendingBatch(anyInt()))
          .thenReturn(List.of(event))
          .thenReturn(Collections.emptyList());

      // 첫 번째 호출: 1건 발행
      relay.publishBatch();
      verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());

      // 두 번째 호출: 대상 없음
      int count = relay.publishBatch();

      assertThat(count).isEqualTo(0);
      // 총 전송 횟수는 여전히 1
      verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }
  }
}
