package com.rodminjo.commerce.order.domain.model;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.domain.OrderErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public class Order {

    private final UUID id;
    private final String customerId;
    private OrderStatus status;
    private final List<OrderLineItem> items;
    private final long totalAmountMinor;
    private final String currency;
    private final Instant createdAt;

    private Order(UUID id, String customerId, OrderStatus status, List<OrderLineItem> items,
                  long totalAmountMinor, String currency, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.items = List.copyOf(items);
        this.totalAmountMinor = totalAmountMinor;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public static Order place(UUID id, String customerId, List<OrderLineItem> items, String currency, Instant now) {
        if (id == null) {
            throw new DomainException(OrderErrorCode.INVALID_ORDER, "id는 필수입니다");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new DomainException(OrderErrorCode.INVALID_ORDER, "customerId는 비어 있을 수 없습니다");
        }
        if (items == null || items.isEmpty()) {
            throw new DomainException(OrderErrorCode.INVALID_ORDER, "주문 항목은 비어 있을 수 없습니다");
        }
        if (currency == null || currency.length() != 3) {
            throw new DomainException(OrderErrorCode.INVALID_ORDER, "currency는 3글자여야 합니다");
        }

        long total = items.stream().mapToLong(OrderLineItem::lineTotalMinor).sum();

        return new Order(
                id,
                customerId,
                OrderStatus.PENDING,
                items,
                total,
                currency,
                now
        );
    }

    public static Order reconstitute(UUID id, String customerId, OrderStatus status,
                                     List<OrderLineItem> items, long totalAmountMinor,
                                     String currency, Instant createdAt) {
        return new Order(id, customerId, status, items, totalAmountMinor, currency, createdAt);
    }

    public void confirm() {
        if (!status.canTransitionTo(OrderStatus.CONFIRMED)) {
            throw new DomainException(OrderErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        if (!status.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new DomainException(OrderErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = OrderStatus.CANCELLED;
    }

}
