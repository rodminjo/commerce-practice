package com.rodminjo.commerce.order.application.service;

import com.google.protobuf.Timestamp;
import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.payment.RefundRequested;
import com.rodminjo.commerce.order.application.port.in.RefundOrderUseCase;
import com.rodminjo.commerce.order.application.port.out.OrderStateRepositoryPort;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import com.rodminjo.commerce.order.domain.model.Order;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 발생 환불 처리. 도메인 가드({@code order.refund()})가 예외(→ 409)를 던지도록 허용(예: PENDING/CANCELLED 환불 시도). 성공
 * 시 {@code refund.requested} 발행 — 결제 서비스가 소비하여 실제 환불을 수행하고 {@code refund.completed}로 응답.
 *
 * <p>{@code refundId}는 주문당 고정({@code orderId + "-refund"})으로 생성하여 결제 측 멱등 처리가 가능하도록 한다(재요청 시 이중 환불
 * 방지). {@code refundId}가 곧 {@code idempotencyKey}.
 */
@RequiredArgsConstructor
@Service
public class RefundOrderService implements RefundOrderUseCase {

  private final OrderStateRepositoryPort orderStateRepositoryPort;
  private final OutboxAppender outboxAppender;
  private final ClockHolder clockHolder;

  @Override
  @Transactional
  public void refund(RefundOrderCommand command) {
    Order order =
        orderStateRepositoryPort
            .findById(command.orderId())
            .orElseThrow(() -> new DomainException(OrderErrorCode.ORDER_NOT_FOUND));

    order.refund();
    orderStateRepositoryPort.update(order);

    String orderId = command.orderId().toString();
    long amountMinor =
        command.amountMinor() != null ? command.amountMinor() : order.getTotalAmountMinor();
    String refundId = orderId + "-refund";

    RefundRequested event =
        RefundRequested.newBuilder()
            .setOrderId(orderId)
            .setRefundId(refundId)
            .setAmountMinor(amountMinor)
            .setReason(command.reason() == null ? "" : command.reason())
            .setIdempotencyKey(refundId)
            .setOccurredAt(now())
            .build();
    outboxAppender.append("Order", orderId, "refund.requested", orderId, event);
  }

  private Timestamp now() {
    Instant instant = clockHolder.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
