package com.rodminjo.commerce.order.application.service;

import com.google.protobuf.Timestamp;
import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase;
import com.rodminjo.commerce.order.application.port.out.OrderStateRepositoryPort;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import com.rodminjo.commerce.order.domain.model.Order;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 발생 취소 처리. Saga 핸들러와 달리 도메인 가드가 예외(→ 409)를 던지도록 허용(예: 이미 COMPLETED). 성공 시 {@code
 * order.cancelled} 발행 — 재고 서비스가 소비하여 예약 재고를 해제.
 */
@RequiredArgsConstructor
@Service
public class CancelOrderService implements CancelOrderUseCase {

  private final OrderStateRepositoryPort orderStateRepositoryPort;
  private final OutboxAppender outboxAppender;
  private final ClockHolder clockHolder;

  @Override
  @Transactional
  public void cancel(CancelOrderCommand command) {
    Order order =
        orderStateRepositoryPort
            .findById(command.orderId())
            .orElseThrow(() -> new DomainException(OrderErrorCode.ORDER_NOT_FOUND));

    order.cancel();
    orderStateRepositoryPort.update(order);

    String orderId = command.orderId().toString();
    OrderCancelled event =
        OrderCancelled.newBuilder()
            .setOrderId(orderId)
            .setReason(command.reason() == null ? "" : command.reason())
            .setOccurredAt(now())
            .build();
    outboxAppender.append("Order", orderId, "order.cancelled", orderId, event);
  }

  private Timestamp now() {
    Instant instant = clockHolder.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
