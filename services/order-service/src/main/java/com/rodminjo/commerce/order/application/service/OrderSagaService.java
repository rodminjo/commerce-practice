package com.rodminjo.commerce.order.application.service;

import com.google.protobuf.Timestamp;
import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.payment.PaymentRequested;
import com.rodminjo.commerce.order.application.port.in.OrderSagaUseCase;
import com.rodminjo.commerce.order.application.port.out.OrderStateRepositoryPort;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga 오케스트레이터(Order가 두뇌 역할). 재고/결제 이벤트에 반응하여 주문을 전진(PENDING → CONFIRMED)하거나 보상(→ CANCELLED, 재고 해제
 * 트리거)시킴.
 *
 * <p>Week-3 멱등성은 <em>부분적</em>: 각 핸들러는 상태 머신으로 보호되어 잘못된 전이를 유발하는 지연/중복 이벤트(최소 1회 전달)를 재적용 없이 무시. 완전한
 * 중복 제거/멱등성 키는 Week 4에서 도입.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class OrderSagaService implements OrderSagaUseCase {

  private final OrderStateRepositoryPort orderStateRepositoryPort;
  private final OutboxAppender outboxAppender;
  private final ClockHolder clockHolder;

  /** 재고 예약 완료 → 주문 총액 결제 요청. */
  @Override
  @Transactional
  public void onInventoryReserved(String orderId) {
    Order order = load(orderId);
    if (order.getStatus() != OrderStatus.PENDING) {
      log.info("Ignoring inventory.reserved for order {} in status {}", orderId, order.getStatus());
      return;
    }
    PaymentRequested event =
        PaymentRequested.newBuilder()
            .setOrderId(order.getId().toString())
            .setAmountMinor(order.getTotalAmountMinor())
            .setCurrency(order.getCurrency())
            .setIdempotencyKey(order.getId().toString())
            .setOccurredAt(now())
            .build();
    outboxAppender.append("Order", orderId, "payment.requested", orderId, event);
  }

  /** 결제 완료 → 주문 확정. 지연/중복 이벤트는 상태 머신 가드가 무시. */
  @Override
  @Transactional
  public void onPaymentCompleted(String orderId) {
    Order order = load(orderId);
    if (!order.getStatus().canTransitionTo(OrderStatus.CONFIRMED)) {
      log.info("Ignoring payment.completed for order {} in status {}", orderId, order.getStatus());
      return;
    }
    order.confirm();
    orderStateRepositoryPort.update(order);
  }

  /** 결제 실패 → 주문 취소 및 order.cancelled 발행(보상: 재고 해제). */
  @Override
  @Transactional
  public void onPaymentFailed(String orderId, String reason) {
    Order order = load(orderId);
    if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
      log.info("Ignoring payment.failed for order {} in status {}", orderId, order.getStatus());
      return;
    }
    order.cancel();
    orderStateRepositoryPort.update(order);
    outboxAppender.append("Order", orderId, "order.cancelled", orderId, cancelled(orderId, reason));
  }

  private Order load(String orderId) {
    return orderStateRepositoryPort
        .findById(UUID.fromString(orderId))
        .orElseThrow(() -> new DomainException(OrderErrorCode.ORDER_NOT_FOUND));
  }

  private OrderCancelled cancelled(String orderId, String reason) {
    return OrderCancelled.newBuilder()
        .setOrderId(orderId)
        .setReason(reason == null ? "" : reason)
        .setOccurredAt(now())
        .build();
  }

  private Timestamp now() {
    Instant instant = clockHolder.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
