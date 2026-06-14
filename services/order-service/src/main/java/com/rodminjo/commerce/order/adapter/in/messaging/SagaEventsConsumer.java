package com.rodminjo.commerce.order.adapter.in.messaging;

import com.rodminjo.commerce.common.outbox.inbox.EventIdHeader;
import com.rodminjo.commerce.common.outbox.inbox.IdempotentConsumer;
import com.rodminjo.commerce.events.inventory.InventoryReserved;
import com.rodminjo.commerce.events.payment.PaymentCompleted;
import com.rodminjo.commerce.events.payment.PaymentFailed;
import com.rodminjo.commerce.events.payment.RefundCompleted;
import com.rodminjo.commerce.events.payment.RefundFailed;
import com.rodminjo.commerce.order.application.port.in.OrderSagaUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga 오케스트레이터에 이벤트를 전달하는 인바운드 어댑터. 소비 이벤트 타입별 리스너 1개씩 등록하며, 타입 전용 컨테이너 팩토리를 통해 Protobuf 페이로드를 구체
 * 클래스로 역직렬화.
 *
 * <p>멱등성: {@link IdempotentConsumer#once}로 동일 {@code x-event-id}를 컨슈머 그룹당 한 번만 처리한다(중복/늦은 재전달 무시).
 * {@link RetryableTopic}으로 일시적 오류는 최대 4회(초기 1회 포함) 지수 백오프 재시도 후 DLT로 격리. dedup 행은 핸들러와 같은 트랜잭션에서
 * 기록되어 핸들러 실패 시 함께 롤백 → 재시도 토픽 재전달 시 재처리가 가능하다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SagaEventsConsumer {

  /** consumerGroup 상수 — {@code spring.kafka.consumer.group-id} 값과 일치. */
  private static final String CONSUMER_GROUP = "order-service";

  private final OrderSagaUseCase orderSaga;
  private final IdempotentConsumer idempotentConsumer;

  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoCreateTopics = "true")
  @KafkaListener(
      topics = "inventory.reserved",
      containerFactory = "inventoryReservedListenerContainerFactory")
  @Transactional
  public void onInventoryReserved(
      InventoryReserved event,
      @Header(name = "x-event-id", required = false) byte[] eventIdHeader) {
    UUID eventId = EventIdHeader.parse(eventIdHeader);
    idempotentConsumer.once(
        CONSUMER_GROUP, eventId, () -> orderSaga.onInventoryReserved(event.getOrderId()));
  }

  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoCreateTopics = "true")
  @KafkaListener(
      topics = "payment.completed",
      containerFactory = "paymentCompletedListenerContainerFactory")
  @Transactional
  public void onPaymentCompleted(
      PaymentCompleted event, @Header(name = "x-event-id", required = false) byte[] eventIdHeader) {
    UUID eventId = EventIdHeader.parse(eventIdHeader);
    idempotentConsumer.once(
        CONSUMER_GROUP, eventId, () -> orderSaga.onPaymentCompleted(event.getOrderId()));
  }

  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoCreateTopics = "true")
  @KafkaListener(
      topics = "payment.failed",
      containerFactory = "paymentFailedListenerContainerFactory")
  @Transactional
  public void onPaymentFailed(
      PaymentFailed event, @Header(name = "x-event-id", required = false) byte[] eventIdHeader) {
    UUID eventId = EventIdHeader.parse(eventIdHeader);
    idempotentConsumer.once(
        CONSUMER_GROUP,
        eventId,
        () -> orderSaga.onPaymentFailed(event.getOrderId(), event.getReason()));
  }

  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoCreateTopics = "true")
  @KafkaListener(
      topics = "refund.completed",
      containerFactory = "refundCompletedListenerContainerFactory")
  @Transactional
  public void onRefundCompleted(
      RefundCompleted event, @Header(name = "x-event-id", required = false) byte[] eventIdHeader) {
    UUID eventId = EventIdHeader.parse(eventIdHeader);
    idempotentConsumer.once(
        CONSUMER_GROUP, eventId, () -> orderSaga.onRefundCompleted(event.getOrderId()));
  }

  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoCreateTopics = "true")
  @KafkaListener(
      topics = "refund.failed",
      containerFactory = "refundFailedListenerContainerFactory")
  @Transactional
  public void onRefundFailed(
      RefundFailed event, @Header(name = "x-event-id", required = false) byte[] eventIdHeader) {
    UUID eventId = EventIdHeader.parse(eventIdHeader);
    idempotentConsumer.once(
        CONSUMER_GROUP,
        eventId,
        () -> orderSaga.onRefundFailed(event.getOrderId(), event.getReason()));
  }

  /**
   * Dead Letter Topic 핸들러. 최대 재시도를 초과한 독성 메시지를 수신하여 WARN 로그로 기록. 추후 알림·모니터링 훅 확장 자리.
   *
   * @param event 처리 불가 이벤트 (Object — DLT 는 타입 소거 후 도착)
   */
  @DltHandler
  public void onDlt(Object event) {
    log.warn("[DLT] order-service: 최대 재시도 초과로 DLT에 격리된 메시지 수신. event={}", event);
  }
}
