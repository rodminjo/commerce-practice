package com.rodminjo.commerce.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import com.rodminjo.commerce.order.fixture.OrderFixture;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderDomainTest {

  private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");

  @Nested
  @DisplayName("Order.place() — 주문 생성 유효성 검사")
  class Place {

    @Test
    @DisplayName("아이템 2개의 totalAmountMinor가 합산된다")
    void place_calculatesTotalAmount() {
      List<OrderLineItem> items =
          List.of(OrderLineItem.of("product-A", 2, 1000L), OrderLineItem.of("product-B", 3, 500L));

      Order order = Order.place(UUID.randomUUID(), "customer-1", items, "KRW", FIXED_NOW);

      assertThat(order.getTotalAmountMinor()).isEqualTo(3500L);
      assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
      assertThat(order.getId()).isNotNull();
      assertThat(order.getCustomerId()).isEqualTo("customer-1");
      assertThat(order.getCurrency()).isEqualTo("KRW");
      assertThat(order.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("빈 아이템 리스트 → DomainException(INVALID_ORDER)")
    void place_emptyItems_throwsDomainException() {
      assertThatThrownBy(
              () -> Order.place(UUID.randomUUID(), "customer-1", List.of(), "KRW", FIXED_NOW))
          .isInstanceOf(DomainException.class)
          .satisfies(
              ex -> {
                DomainException de = (DomainException) ex;
                assertThat(de.errorCode().code()).isEqualTo("INVALID_ORDER");
              });
    }

    @Test
    @DisplayName("blank customerId → DomainException(INVALID_ORDER)")
    void place_blankCustomerId_throwsDomainException() {
      List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));

      assertThatThrownBy(() -> Order.place(UUID.randomUUID(), "  ", items, "KRW", FIXED_NOW))
          .isInstanceOf(DomainException.class)
          .satisfies(
              ex -> {
                DomainException de = (DomainException) ex;
                assertThat(de.errorCode().code()).isEqualTo("INVALID_ORDER");
              });
    }

    @Test
    @DisplayName("null customerId → DomainException(INVALID_ORDER)")
    void place_nullCustomerId_throwsDomainException() {
      List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));

      assertThatThrownBy(() -> Order.place(UUID.randomUUID(), null, items, "KRW", FIXED_NOW))
          .isInstanceOf(DomainException.class)
          .satisfies(
              ex -> {
                DomainException de = (DomainException) ex;
                assertThat(de.errorCode().code()).isEqualTo("INVALID_ORDER");
              });
    }

    @Test
    @DisplayName("currency가 3글자가 아니면 → DomainException(INVALID_ORDER)")
    void place_invalidCurrency_throwsDomainException() {
      List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));

      assertThatThrownBy(() -> Order.place(UUID.randomUUID(), "customer-1", items, "KR", FIXED_NOW))
          .isInstanceOf(DomainException.class)
          .satisfies(
              ex -> {
                DomainException de = (DomainException) ex;
                assertThat(de.errorCode().code()).isEqualTo("INVALID_ORDER");
              });
    }
  }

  @Nested
  @DisplayName("OrderLineItem 유효성 검사")
  class OrderLineItemValidation {

    @Test
    @DisplayName("quantity <= 0 → DomainException")
    void orderLineItem_invalidQuantity_throwsDomainException() {
      assertThatThrownBy(() -> OrderLineItem.of("product-A", 0, 1000L))
          .isInstanceOf(DomainException.class);

      assertThatThrownBy(() -> OrderLineItem.of("product-A", -1, 1000L))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("unitPriceMinor < 0 → DomainException")
    void orderLineItem_negativePrice_throwsDomainException() {
      assertThatThrownBy(() -> OrderLineItem.of("product-A", 1, -1L))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("blank productId → DomainException")
    void orderLineItem_blankProductId_throwsDomainException() {
      assertThatThrownBy(() -> OrderLineItem.of("", 1, 1000L)).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> OrderLineItem.of("  ", 1, 1000L))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("lineTotalMinor = quantity * unitPriceMinor")
    void orderLineItem_lineTotalMinor() {
      OrderLineItem item = OrderLineItem.of("product-A", 3, 500L);
      assertThat(item.lineTotalMinor()).isEqualTo(1500L);
    }
  }

  @Nested
  @DisplayName("상태 전이 — canTransitionTo 빠른 검증")
  class StateTransitionQuick {

    @Test
    @DisplayName("PENDING → CONFIRMED 가능")
    void orderStatus_pendingCanTransitionToConfirmed() {
      assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    @DisplayName("PENDING → COMPLETED 불가")
    void orderStatus_pendingCannotTransitionToCompleted() {
      assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("PENDING → CANCELLED 가능")
    void orderStatus_pendingCanTransitionToCancelled() {
      assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }
  }

  @Nested
  @DisplayName("confirm() — 주문 확정")
  class Confirm {

    @Test
    @DisplayName("PENDING 상태에서 confirm() → CONFIRMED")
    void confirm_fromPending_becomesConfirmed() {
      Order order = OrderFixture.order();

      order.confirm();

      assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("CONFIRMED 상태에서 confirm() 재호출 → DomainException(INVALID_STATE_TRANSITION)")
    void confirm_alreadyConfirmed_throwsDomainException() {
      Order order = OrderFixture.order();
      order.confirm();

      assertThatThrownBy(order::confirm)
          .isInstanceOf(DomainException.class)
          .satisfies(
              ex -> {
                DomainException de = (DomainException) ex;
                assertThat(de.errorCode().code()).isEqualTo("INVALID_STATE_TRANSITION");
              });
    }
  }

  @Nested
  @DisplayName("cancel() — 주문 취소")
  class Cancel {

    @Test
    @DisplayName("PENDING 상태에서 cancel() → CANCELLED")
    void cancel_fromPending_becomesCancelled() {
      Order order = OrderFixture.order();

      order.cancel();

      assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("CANCELLED 상태에서 cancel() 재호출 → DomainException(INVALID_STATE_TRANSITION)")
    void cancel_alreadyCancelled_throwsDomainException() {
      Order order = OrderFixture.order();
      order.cancel();

      assertThatThrownBy(order::cancel)
          .isInstanceOf(DomainException.class)
          .satisfies(
              ex -> {
                DomainException de = (DomainException) ex;
                assertThat(de.errorCode().code()).isEqualTo("INVALID_STATE_TRANSITION");
              });
    }
  }

  @Nested
  @DisplayName("refund() — 주문 환불")
  class Refund {

    private static Order orderWith(OrderStatus status) {
      return Order.reconstitute(
          UUID.randomUUID(),
          "customer-1",
          status,
          List.of(OrderLineItem.of("product-A", 2, 1000L)),
          2000L,
          "KRW",
          FIXED_NOW);
    }

    @Test
    @DisplayName("CONFIRMED 상태에서 refund() → REFUNDED")
    void refund_fromConfirmed_becomesRefunded() {
      Order order = orderWith(OrderStatus.CONFIRMED);

      order.refund();

      assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("COMPLETED 상태에서 refund() → REFUNDED")
    void refund_fromCompleted_becomesRefunded() {
      Order order = orderWith(OrderStatus.COMPLETED);

      order.refund();

      assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("PENDING 상태에서 refund() → DomainException(INVALID_STATE_TRANSITION)")
    void refund_fromPending_throwsDomainException() {
      Order order = orderWith(OrderStatus.PENDING);

      assertThatThrownBy(order::refund)
          .isInstanceOf(DomainException.class)
          .satisfies(
              ex -> {
                DomainException de = (DomainException) ex;
                assertThat(de.errorCode().code()).isEqualTo("INVALID_STATE_TRANSITION");
              });
    }

    @Test
    @DisplayName("REFUNDED 상태에서 refund() 재호출 → DomainException(INVALID_STATE_TRANSITION)")
    void refund_alreadyRefunded_throwsDomainException() {
      Order order = orderWith(OrderStatus.REFUNDED);

      assertThatThrownBy(order::refund)
          .isInstanceOf(DomainException.class)
          .satisfies(
              ex -> {
                DomainException de = (DomainException) ex;
                assertThat(de.errorCode().code()).isEqualTo("INVALID_STATE_TRANSITION");
              });
    }
  }

  @Nested
  @DisplayName("reconstitute() — 주문 재구성")
  class Reconstitute {

    @Test
    @DisplayName("모든 필드로 Order를 재구성한다")
    void reconstitute_rebuildsOrder() {
      UUID id = UUID.randomUUID();
      java.time.Instant now = java.time.Instant.now();
      List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 2, 1000L));

      Order order =
          Order.reconstitute(id, "customer-1", OrderStatus.CONFIRMED, items, 2000L, "KRW", now);

      assertThat(order.getId()).isEqualTo(id);
      assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
      assertThat(order.getTotalAmountMinor()).isEqualTo(2000L);
      assertThat(order.getCreatedAt()).isEqualTo(now);
    }
  }
}
