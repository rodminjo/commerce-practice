package com.rodminjo.commerce.order.application.service;

import com.google.protobuf.Timestamp;
import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.payment.RefundRequested;
import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase;
import com.rodminjo.commerce.order.application.port.out.OrderStateRepositoryPort;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 발생 취소 처리. Saga 핸들러와 달리 도메인 가드가 예외(→ 409)를 던지도록 허용(예: 이미 COMPLETED). 성공 시 {@code
 * order.cancelled} 발행 — 재고 서비스가 소비하여 예약 재고를 해제.
 *
 * <p>보상 심화: 취소 직전 상태가 {@code CONFIRMED}(결제 완료)였다면 재고 복구({@code order.cancelled})뿐 아니라 환불({@code
 * refund.requested})도 같은 트랜잭션에서 적재한다. {@code PENDING}(미결제) 단계 취소는 환불 대상이 없어 {@code
 * order.cancelled}만 적재한다. 환불 {@code refundId}는 주문당 고정({@code orderId + "-cancel-refund"})으로 생성하여 결제
 * 측 멱등 처리가 가능하다.
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

    // 취소 전 상태를 캡처: CONFIRMED(결제 완료)였으면 재고 복구 + 환불 둘 다 보상해야 한다.
    OrderStatus statusBeforeCancel = order.getStatus();

    order.cancel();
    orderStateRepositoryPort.update(order);

    String orderId = command.orderId().toString();
    String reason = command.reason() == null ? "" : command.reason();

    OrderCancelled cancelled =
        OrderCancelled.newBuilder()
            .setOrderId(orderId)
            .setReason(reason)
            .setOccurredAt(now())
            .build();
    outboxAppender.append("Order", orderId, "order.cancelled", orderId, cancelled);

    if (statusBeforeCancel == OrderStatus.CONFIRMED) {
      String refundId = orderId + "-cancel-refund";
      RefundRequested refund =
          RefundRequested.newBuilder()
              .setOrderId(orderId)
              .setRefundId(refundId)
              .setAmountMinor(order.getTotal().amountMinor())
              .setReason(reason)
              .setIdempotencyKey(refundId)
              .setOccurredAt(now())
              .build();
      outboxAppender.append("Order", orderId, "refund.requested", orderId, refund);
    }
  }

  private Timestamp now() {
    Instant instant = clockHolder.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
