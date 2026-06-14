package com.rodminjo.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.payment.RefundRequested;
import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase.CancelOrderCommand;
import com.rodminjo.commerce.order.application.service.support.FakeOrderStateRepository;
import com.rodminjo.commerce.order.application.service.support.FakeOutboxAppender;
import com.rodminjo.commerce.order.application.service.support.FakeOutboxAppender.Appended;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import com.rodminjo.commerce.order.domain.model.Money;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CancelOrderServiceTest {

  private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

  private final FakeOrderStateRepository orderStateRepository = new FakeOrderStateRepository();
  private final FakeOutboxAppender outboxAppender = new FakeOutboxAppender();
  private final ClockHolder clockHolder = () -> NOW;

  private CancelOrderService service;

  @BeforeEach
  void setUp() {
    service = new CancelOrderService(orderStateRepository, outboxAppender, clockHolder);
  }

  private static Order orderWith(OrderStatus status) {
    return Order.reconstitute(
        ORDER_ID,
        "customer-1",
        status,
        List.of(OrderLineItem.of("prod-1", 1, Money.of(1000L, "KRW"))),
        1000L,
        "KRW",
        NOW);
  }

  @Nested
  @DisplayName("정상 취소")
  class SuccessfulCancellation {

    @Test
    @DisplayName("PENDING 주문 취소 → CANCELLED + order.cancelled 적재")
    void cancelPendingOrder() {
      orderStateRepository.seed(orderWith(OrderStatus.PENDING));

      service.cancel(new CancelOrderCommand(ORDER_ID, "changed-mind"));

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(outboxAppender.appended()).hasSize(1);
      Appended appended = outboxAppender.appended().get(0);
      assertThat(appended.aggregateType()).isEqualTo("Order");
      assertThat(appended.aggregateId()).isEqualTo(ORDER_ID.toString());
      assertThat(appended.topic()).isEqualTo("order.cancelled");
      assertThat(appended.partitionKey()).isEqualTo(ORDER_ID.toString());
      assertThat(((OrderCancelled) appended.event()).getReason()).isEqualTo("changed-mind");
    }

    @Test
    @DisplayName(
        "CONFIRMED(결제 완료) 주문 취소 → CANCELLED + order.cancelled + refund.requested 동시 적재 (보상 심화)")
    void cancelConfirmedOrderCompensatesWithRefund() {
      orderStateRepository.seed(orderWith(OrderStatus.CONFIRMED));

      service.cancel(new CancelOrderCommand(ORDER_ID, "out-of-stock"));

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(outboxAppender.appended()).hasSize(2);

      Appended cancelled = outboxAppender.appended().get(0);
      assertThat(cancelled.topic()).isEqualTo("order.cancelled");
      assertThat(((OrderCancelled) cancelled.event()).getReason()).isEqualTo("out-of-stock");

      Appended refund = outboxAppender.appended().get(1);
      assertThat(refund.aggregateType()).isEqualTo("Order");
      assertThat(refund.aggregateId()).isEqualTo(ORDER_ID.toString());
      assertThat(refund.topic()).isEqualTo("refund.requested");
      assertThat(refund.partitionKey()).isEqualTo(ORDER_ID.toString());
      RefundRequested event = (RefundRequested) refund.event();
      assertThat(event.getOrderId()).isEqualTo(ORDER_ID.toString());
      assertThat(event.getAmountMinor()).isEqualTo(1000L);
      assertThat(event.getReason()).isEqualTo("out-of-stock");
      // refundId/idempotencyKey 는 주문당 고정(취소 환불) → 결제 측 멱등.
      assertThat(event.getRefundId()).isEqualTo(ORDER_ID + "-cancel-refund");
      assertThat(event.getIdempotencyKey()).isEqualTo(event.getRefundId());
    }
  }

  @Nested
  @DisplayName("취소 실패 — 도메인/조회 오류")
  class CancellationFailure {

    @Test
    @DisplayName("존재하지 않는 주문 → ORDER_NOT_FOUND")
    void cancelMissingOrder() {
      assertThatThrownBy(() -> service.cancel(new CancelOrderCommand(ORDER_ID, "x")))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("COMPLETED 주문 취소 시도 → INVALID_STATE_TRANSITION (도메인 가드)")
    void cancelCompletedOrderRejected() {
      orderStateRepository.seed(orderWith(OrderStatus.COMPLETED));

      assertThatThrownBy(() -> service.cancel(new CancelOrderCommand(ORDER_ID, "x")))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(OrderErrorCode.INVALID_STATE_TRANSITION);

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.COMPLETED);
      assertThat(outboxAppender.appended()).isEmpty();
    }
  }
}
