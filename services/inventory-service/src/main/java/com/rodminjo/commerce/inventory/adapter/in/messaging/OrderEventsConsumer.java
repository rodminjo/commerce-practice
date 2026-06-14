package com.rodminjo.commerce.inventory.adapter.in.messaging;

import com.rodminjo.commerce.common.outbox.inbox.EventIdHeader;
import com.rodminjo.commerce.common.outbox.inbox.IdempotentConsumer;
import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.order.OrderPlaced;
import com.rodminjo.commerce.inventory.application.port.in.ReleaseStockUseCase;
import com.rodminjo.commerce.inventory.application.port.in.ReleaseStockUseCase.ReleaseStockCommand;
import com.rodminjo.commerce.inventory.application.port.in.ReserveStockUseCase;
import com.rodminjo.commerce.inventory.application.port.in.ReserveStockUseCase.ReserveStockCommand;
import com.rodminjo.commerce.inventory.application.port.in.ReserveStockUseCase.ReserveStockCommand.Line;
import java.util.List;
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
 * 인바운드 어댑터: Order 이벤트를 인벤토리 유스케이스 호출로 변환. 각 리스너는 타입별 컨테이너 팩토리를 사용하여 {@code
 * KafkaProtobufDeserializer}가 {@code DynamicMessage} 대신 구체 이벤트 타입을 반환.
 *
 * <p>멱등성: {@link IdempotentConsumer#once}로 동일 {@code x-event-id}를 중복 처리하지 않는다. {@link
 * RetryableTopic}으로 일시적 오류는 최대 4회(초기 1회 포함) 지수 백오프 재시도 후 DLT로 격리.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventsConsumer {

  /** consumerGroup 상수 — {@code spring.kafka.consumer.group-id} 값과 일치. */
  private static final String CONSUMER_GROUP = "inventory-service";

  private final ReserveStockUseCase reserveStockUseCase;
  private final ReleaseStockUseCase releaseStockUseCase;
  private final IdempotentConsumer idempotentConsumer;

  /**
   * {@code order.placed} 이벤트 수신 → 재고 예약. 동일 {@code x-event-id}로 재전달 시 dedup으로 skip.
   *
   * @param event 주문 생성 이벤트 (Protobuf)
   * @param eventIdHeader {@code x-event-id} 헤더 bytes (릴레이가 항상 적재; 부재 시 명시적 예외)
   */
  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoCreateTopics = "true")
  @KafkaListener(topics = "order.placed", containerFactory = "orderPlacedListenerContainerFactory")
  @Transactional
  public void onOrderPlaced(
      OrderPlaced event, @Header(name = "x-event-id", required = false) byte[] eventIdHeader) {
    UUID eventId = EventIdHeader.parse(eventIdHeader);
    idempotentConsumer.once(
        CONSUMER_GROUP,
        eventId,
        () -> {
          List<Line> items =
              event.getItemsList().stream()
                  .map(item -> new Line(item.getProductId(), item.getQuantity()))
                  .toList();
          reserveStockUseCase.reserve(new ReserveStockCommand(event.getOrderId(), items));
        });
  }

  /**
   * {@code order.cancelled} 이벤트 수신 → 재고 복구(보상). 동일 {@code x-event-id}로 재전달 시 dedup으로 skip.
   *
   * @param event 주문 취소 이벤트 (Protobuf)
   * @param eventIdHeader {@code x-event-id} 헤더 bytes (릴레이가 항상 적재; 부재 시 명시적 예외)
   */
  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoCreateTopics = "true")
  @KafkaListener(
      topics = "order.cancelled",
      containerFactory = "orderCancelledListenerContainerFactory")
  @Transactional
  public void onOrderCancelled(
      OrderCancelled event, @Header(name = "x-event-id", required = false) byte[] eventIdHeader) {
    UUID eventId = EventIdHeader.parse(eventIdHeader);
    idempotentConsumer.once(
        CONSUMER_GROUP,
        eventId,
        () ->
            releaseStockUseCase.release(
                new ReleaseStockCommand(event.getOrderId(), event.getReason())));
  }

  /**
   * Dead Letter Topic 핸들러. 최대 재시도를 초과한 독성 메시지를 수신하여 WARN 로그로 기록. 추후 알림·모니터링 훅 확장 자리.
   *
   * @param event 처리 불가 이벤트 (Object — DLT 는 타입 소거 후 도착)
   */
  @DltHandler
  public void onDlt(Object event) {
    log.warn("[DLT] inventory-service: 최대 재시도 초과로 DLT에 격리된 메시지 수신. event={}", event);
  }
}
