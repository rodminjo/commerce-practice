package com.rodminjo.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.payment.RefundRequested;
import com.rodminjo.commerce.order.application.port.in.RefundOrderUseCase.RefundOrderCommand;
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

class RefundOrderServiceTest {

  private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

  private final FakeOrderStateRepository orderStateRepository = new FakeOrderStateRepository();
  private final FakeOutboxAppender outboxAppender = new FakeOutboxAppender();
  private final ClockHolder clockHolder = () -> NOW;

  private RefundOrderService service;

  @BeforeEach
  void setUp() {
    service = new RefundOrderService(orderStateRepository, outboxAppender, clockHolder);
  }

  private static Order orderWith(OrderStatus status) {
    return Order.reconstitute(
        ORDER_ID,
        "customer-1",
        status,
        List.of(OrderLineItem.of("prod-1", 2, Money.of(1000L, "KRW"))),
        2000L,
        "KRW",
        NOW);
  }

  @Nested
  @DisplayName("정상 환불")
  class SuccessfulRefund {

    @Test
    @DisplayName("CONFIRMED 주문 전체 환불 → REFUNDED + refund.requested(전체 금액) 적재")
    void refundConfirmedOrderFullAmount() {
      orderStateRepository.seed(orderWith(OrderStatus.CONFIRMED));

      service.refund(new RefundOrderCommand(ORDER_ID, null, "customer-request"));

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.REFUNDED);
      assertThat(outboxAppender.appended()).hasSize(1);
      Appended appended = outboxAppender.appended().get(0);
      assertThat(appended.aggregateType()).isEqualTo("Order");
      assertThat(appended.aggregateId()).isEqualTo(ORDER_ID.toString());
      assertThat(appended.topic()).isEqualTo("refund.requested");
      assertThat(appended.partitionKey()).isEqualTo(ORDER_ID.toString());
      RefundRequested event = (RefundRequested) appended.event();
      assertThat(event.getOrderId()).isEqualTo(ORDER_ID.toString());
      assertThat(event.getAmountMinor()).isEqualTo(2000L);
      assertThat(event.getReason()).isEqualTo("customer-request");
      assertThat(event.getRefundId()).isEqualTo(ORDER_ID + "-refund");
      assertThat(event.getIdempotencyKey()).isEqualTo(event.getRefundId());
    }

    @Test
    @DisplayName("COMPLETED 주문 부분 환불 → REFUNDED + refund.requested(부분 금액) 적재")
    void refundCompletedOrderPartialAmount() {
      orderStateRepository.seed(orderWith(OrderStatus.COMPLETED));

      service.refund(new RefundOrderCommand(ORDER_ID, 500L, null));

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.REFUNDED);
      RefundRequested event = (RefundRequested) outboxAppender.appended().get(0).event();
      assertThat(event.getAmountMinor()).isEqualTo(500L);
      assertThat(event.getReason()).isEmpty();
    }
  }

  @Nested
  @DisplayName("환불 실패 — 도메인/조회 오류")
  class RefundFailure {

    @Test
    @DisplayName("존재하지 않는 주문 → ORDER_NOT_FOUND")
    void refundMissingOrder() {
      assertThatThrownBy(() -> service.refund(new RefundOrderCommand(ORDER_ID, null, "x")))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("PENDING 주문 환불 시도 → INVALID_STATE_TRANSITION (도메인 가드), outbox 미적재")
    void refundPendingOrderRejected() {
      orderStateRepository.seed(orderWith(OrderStatus.PENDING));

      assertThatThrownBy(() -> service.refund(new RefundOrderCommand(ORDER_ID, null, "x")))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(OrderErrorCode.INVALID_STATE_TRANSITION);

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.PENDING);
      assertThat(outboxAppender.appended()).isEmpty();
    }
  }
}
