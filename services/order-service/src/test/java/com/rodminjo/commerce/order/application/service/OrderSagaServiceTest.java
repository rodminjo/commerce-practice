package com.rodminjo.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.payment.PaymentRequested;
import com.rodminjo.commerce.order.application.service.support.FakeOrderStateRepository;
import com.rodminjo.commerce.order.application.service.support.FakeOutboxAppender;
import com.rodminjo.commerce.order.application.service.support.FakeOutboxAppender.Appended;
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

class OrderSagaServiceTest {

  private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

  private final FakeOrderStateRepository orderStateRepository = new FakeOrderStateRepository();
  private final FakeOutboxAppender outboxAppender = new FakeOutboxAppender();
  private final ClockHolder clockHolder = () -> NOW;

  private OrderSagaService service;

  @BeforeEach
  void setUp() {
    service = new OrderSagaService(orderStateRepository, outboxAppender, clockHolder);
  }

  private static Order orderWith(OrderStatus status) {
    return Order.reconstitute(
        ORDER_ID,
        "customer-1",
        status,
        List.of(OrderLineItem.of("prod-1", 2, 1000L)),
        2000L,
        "KRW",
        NOW);
  }

  @Nested
  @DisplayName("onInventoryReserved — 재고 예약 완료 처리")
  class OnInventoryReserved {

    @Test
    @DisplayName("PENDING 주문 → payment.requested 적재 (amount/idempotencyKey 채움)")
    void onInventoryReservedRequestsPayment() {
      orderStateRepository.seed(orderWith(OrderStatus.PENDING));

      service.onInventoryReserved(ORDER_ID.toString());

      assertThat(outboxAppender.appended()).hasSize(1);
      Appended appended = outboxAppender.appended().get(0);
      assertThat(appended.aggregateType()).isEqualTo("Order");
      assertThat(appended.aggregateId()).isEqualTo(ORDER_ID.toString());
      assertThat(appended.topic()).isEqualTo("payment.requested");
      assertThat(appended.partitionKey()).isEqualTo(ORDER_ID.toString());
      PaymentRequested event = (PaymentRequested) appended.event();
      assertThat(event.getAmountMinor()).isEqualTo(2000L);
      assertThat(event.getCurrency()).isEqualTo("KRW");
      assertThat(event.getIdempotencyKey()).isEqualTo(ORDER_ID.toString());
    }

    @Test
    @DisplayName("이미 CONFIRMED 상태 → 가드로 무시, outbox 미적재")
    void onInventoryReservedIgnoredWhenNotPending() {
      orderStateRepository.seed(orderWith(OrderStatus.CONFIRMED));

      service.onInventoryReserved(ORDER_ID.toString());

      assertThat(outboxAppender.appended()).isEmpty();
    }
  }

  @Nested
  @DisplayName("onPaymentCompleted — 결제 완료 처리")
  class OnPaymentCompleted {

    @Test
    @DisplayName("PENDING 주문 → CONFIRMED로 상태 갱신")
    void onPaymentCompletedConfirms() {
      orderStateRepository.seed(orderWith(OrderStatus.PENDING));

      service.onPaymentCompleted(ORDER_ID.toString());

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("이미 CONFIRMED 상태 → 늦은 중복 무시, 상태 불변")
    void onPaymentCompletedIgnoresDuplicate() {
      orderStateRepository.seed(orderWith(OrderStatus.CONFIRMED));

      service.onPaymentCompleted(ORDER_ID.toString());

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
  }

  @Nested
  @DisplayName("onPaymentFailed — 결제 실패 처리")
  class OnPaymentFailed {

    @Test
    @DisplayName("PENDING 주문 → CANCELLED + order.cancelled 적재 (보상 트리거)")
    void onPaymentFailedCancelsAndCompensates() {
      orderStateRepository.seed(orderWith(OrderStatus.PENDING));

      service.onPaymentFailed(ORDER_ID.toString(), "card-declined");

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(outboxAppender.appended()).hasSize(1);
      Appended appended = outboxAppender.appended().get(0);
      assertThat(appended.aggregateType()).isEqualTo("Order");
      assertThat(appended.aggregateId()).isEqualTo(ORDER_ID.toString());
      assertThat(appended.topic()).isEqualTo("order.cancelled");
      assertThat(appended.partitionKey()).isEqualTo(ORDER_ID.toString());
      assertThat(((OrderCancelled) appended.event()).getReason()).isEqualTo("card-declined");
    }

    @Test
    @DisplayName("이미 CANCELLED 상태 → 가드로 무시, 상태 불변 및 outbox 미적재")
    void onPaymentFailedIgnoredWhenTerminal() {
      orderStateRepository.seed(orderWith(OrderStatus.CANCELLED));

      service.onPaymentFailed(ORDER_ID.toString(), "card-declined");

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(outboxAppender.appended()).isEmpty();
    }
  }

  @Nested
  @DisplayName("onRefundCompleted — 환불 완료 처리")
  class OnRefundCompleted {

    @Test
    @DisplayName("CONFIRMED 주문 → REFUNDED로 상태 갱신")
    void onRefundCompletedConfirmsRefund() {
      orderStateRepository.seed(orderWith(OrderStatus.CONFIRMED));

      service.onRefundCompleted(ORDER_ID.toString());

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("이미 REFUNDED 상태 → 중복 무시, 상태 불변")
    void onRefundCompletedIgnoresDuplicate() {
      orderStateRepository.seed(orderWith(OrderStatus.REFUNDED));

      service.onRefundCompleted(ORDER_ID.toString());

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }
  }

  @Nested
  @DisplayName("onRefundFailed — 환불 실패 처리")
  class OnRefundFailed {

    @Test
    @DisplayName("상태 유지(로깅만), outbox 미적재")
    void onRefundFailedKeepsState() {
      orderStateRepository.seed(orderWith(OrderStatus.CONFIRMED));

      service.onRefundFailed(ORDER_ID.toString(), "gateway-error");

      Order stored = orderStateRepository.findById(ORDER_ID).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
      assertThat(outboxAppender.appended()).isEmpty();
    }
  }
}
