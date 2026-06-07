package com.rodminjo.commerce.order.domain;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDomainTest {

    private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    @DisplayName("place(): 아이템 2개의 totalAmountMinor가 합산된다")
    void place_calculatesTotalAmount() {
        List<OrderLineItem> items = List.of(
                OrderLineItem.of("product-A", 2, 1000L),
                OrderLineItem.of("product-B", 3, 500L)
        );

        Order order = Order.place(java.util.UUID.randomUUID(),"customer-1", items, "KRW", FIXED_NOW);

        assertThat(order.getTotalAmountMinor()).isEqualTo(3500L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getId()).isNotNull();
        assertThat(order.getCustomerId()).isEqualTo("customer-1");
        assertThat(order.getCurrency()).isEqualTo("KRW");
        assertThat(order.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("place(): 빈 아이템 리스트 → DomainException(INVALID_ORDER)")
    void place_emptyItems_throwsDomainException() {
        assertThatThrownBy(() -> Order.place(java.util.UUID.randomUUID(),"customer-1", List.of(), "KRW", FIXED_NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.errorCode().code()).isEqualTo("INVALID_ORDER");
                });
    }

    @Test
    @DisplayName("place(): blank customerId → DomainException")
    void place_blankCustomerId_throwsDomainException() {
        List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));

        assertThatThrownBy(() -> Order.place(java.util.UUID.randomUUID(),"  ", items, "KRW", FIXED_NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.errorCode().code()).isEqualTo("INVALID_ORDER");
                });
    }

    @Test
    @DisplayName("place(): null customerId → DomainException")
    void place_nullCustomerId_throwsDomainException() {
        List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));

        assertThatThrownBy(() -> Order.place(java.util.UUID.randomUUID(),null, items, "KRW", FIXED_NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.errorCode().code()).isEqualTo("INVALID_ORDER");
                });
    }

    @Test
    @DisplayName("place(): currency가 3글자가 아니면 → DomainException")
    void place_invalidCurrency_throwsDomainException() {
        List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));

        assertThatThrownBy(() -> Order.place(java.util.UUID.randomUUID(),"customer-1", items, "KR", FIXED_NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.errorCode().code()).isEqualTo("INVALID_ORDER");
                });
    }

    @Test
    @DisplayName("OrderLineItem: quantity <= 0 → DomainException")
    void orderLineItem_invalidQuantity_throwsDomainException() {
        assertThatThrownBy(() -> OrderLineItem.of("product-A", 0, 1000L))
                .isInstanceOf(DomainException.class);

        assertThatThrownBy(() -> OrderLineItem.of("product-A", -1, 1000L))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("OrderLineItem: unitPriceMinor < 0 → DomainException")
    void orderLineItem_negativePrice_throwsDomainException() {
        assertThatThrownBy(() -> OrderLineItem.of("product-A", 1, -1L))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("OrderLineItem: blank productId → DomainException")
    void orderLineItem_blankProductId_throwsDomainException() {
        assertThatThrownBy(() -> OrderLineItem.of("", 1, 1000L))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> OrderLineItem.of("  ", 1, 1000L))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("OrderLineItem: lineTotalMinor = quantity * unitPriceMinor")
    void orderLineItem_lineTotalMinor() {
        OrderLineItem item = OrderLineItem.of("product-A", 3, 500L);
        assertThat(item.lineTotalMinor()).isEqualTo(1500L);
    }

    @Test
    @DisplayName("상태 전이: PENDING.canTransitionTo(CONFIRMED)==true")
    void orderStatus_pendingCanTransitionToConfirmed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    @DisplayName("상태 전이: PENDING.canTransitionTo(COMPLETED)==false")
    void orderStatus_pendingCannotTransitionToCompleted() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("상태 전이: PENDING.canTransitionTo(CANCELLED)==true")
    void orderStatus_pendingCanTransitionToCancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("confirm(): PENDING 상태에서 confirm() → status CONFIRMED")
    void confirm_fromPending_becomesConfirmed() {
        List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));
        Order order = Order.place(java.util.UUID.randomUUID(),"customer-1", items, "KRW", FIXED_NOW);

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("confirm(): CONFIRMED 상태에서 confirm() 재호출 → DomainException")
    void confirm_alreadyConfirmed_throwsDomainException() {
        List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));
        Order order = Order.place(java.util.UUID.randomUUID(),"customer-1", items, "KRW", FIXED_NOW);
        order.confirm();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.errorCode().code()).isEqualTo("INVALID_STATE_TRANSITION");
                });
    }

    @Test
    @DisplayName("cancel(): PENDING 상태에서 cancel() → status CANCELLED")
    void cancel_fromPending_becomesCancelled() {
        List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));
        Order order = Order.place(java.util.UUID.randomUUID(),"customer-1", items, "KRW", FIXED_NOW);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel(): CANCELLED 상태에서 cancel() 재호출 → DomainException")
    void cancel_alreadyCancelled_throwsDomainException() {
        List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 1, 1000L));
        Order order = Order.place(java.util.UUID.randomUUID(),"customer-1", items, "KRW", FIXED_NOW);
        order.cancel();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.errorCode().code()).isEqualTo("INVALID_STATE_TRANSITION");
                });
    }

    @Test
    @DisplayName("reconstitute(): 모든 필드로 Order를 재구성한다")
    void reconstitute_rebuildsOrder() {
        java.util.UUID id = java.util.UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        List<OrderLineItem> items = List.of(OrderLineItem.of("product-A", 2, 1000L));

        Order order = Order.reconstitute(id, "customer-1", OrderStatus.CONFIRMED, items, 2000L, "KRW", now);

        assertThat(order.getId()).isEqualTo(id);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTotalAmountMinor()).isEqualTo(2000L);
        assertThat(order.getCreatedAt()).isEqualTo(now);
    }
}
