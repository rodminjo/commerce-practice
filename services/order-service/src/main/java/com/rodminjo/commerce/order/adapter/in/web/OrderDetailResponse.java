package com.rodminjo.commerce.order.adapter.in.web;

import com.rodminjo.commerce.order.domain.model.OrderStatus;

import java.util.List;
import java.util.UUID;

public record OrderDetailResponse(
        UUID orderId,
        OrderStatus status,
        List<OrderItemResponse> items,
        long totalAmountMinor,
        String currency
) {
    public record OrderItemResponse(
            String productId,
            int quantity,
            long unitPriceMinor
    ) {}
}
