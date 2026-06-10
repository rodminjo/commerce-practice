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
 * The Saga orchestrator (Order is the brain). Reacts to inventory/payment events and drives the
 * order forward (PENDING → CONFIRMED) or compensates (→ CANCELLED, which triggers stock release).
 *
 * <p>Week-3 idempotency is <em>partial</em>: every handler is guarded by the state machine so a
 * late/duplicate event (at-least-once delivery) that would cause an illegal transition is ignored
 * rather than reapplied. Full dedup / idempotency keys land in Week 4.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class OrderSagaService implements OrderSagaUseCase {

  private final OrderStateRepositoryPort orderStateRepositoryPort;
  private final OutboxAppender outboxAppender;
  private final ClockHolder clockHolder;

  /** Inventory reserved → request payment for the order total. */
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

  /** Payment completed → confirm the order. Late/duplicate events are ignored by the guard. */
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

  /** Payment failed → cancel the order and emit order.cancelled (compensation: stock release). */
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
