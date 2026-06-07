package com.rodminjo.commerce.order.domain.model;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import lombok.Getter;

@Getter
public final class OrderLineItem {

    private final String productId;
    private final int quantity;
    private final long unitPriceMinor;

    private OrderLineItem(String productId, int quantity, long unitPriceMinor) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
    }

    public static OrderLineItem of(String productId, int quantity, long unitPriceMinor) {
        if (productId == null || productId.isBlank()) {
            throw new DomainException(OrderErrorCode.INVALID_ORDER, "productId는 비어 있을 수 없습니다");
        }
        if (quantity <= 0) {
            throw new DomainException(OrderErrorCode.INVALID_ORDER, "quantity는 0보다 커야 합니다");
        }
        if (unitPriceMinor < 0) {
            throw new DomainException(OrderErrorCode.INVALID_ORDER, "unitPriceMinor는 0 이상이어야 합니다");
        }
        return new OrderLineItem(productId, quantity, unitPriceMinor);
    }

    public long lineTotalMinor() {
        return (long) quantity * unitPriceMinor;
    }
}
