package com.rodminjo.commerce.order.application.port.in;

import com.rodminjo.commerce.order.domain.model.OrderStatus;

import java.util.List;
import java.util.UUID;

public interface GetOrderUseCase {

    OrderView getOrder(UUID orderId);

    record OrderView(
            UUID orderId,
            OrderStatus status,
            List<OrderItemView> items,
            long totalAmountMinor,
            String currency
    ) {
        public record OrderItemView(
                String productId,
                int quantity,
                long unitPriceMinor
        ) {}
    }
}
