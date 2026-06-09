package com.rodminjo.commerce.order.application.port.in;

import java.util.List;
import java.util.UUID;

public interface PlaceOrderUseCase {

  PlaceOrderResult place(PlaceOrderCommand command);

  record PlaceOrderCommand(String customerId, List<OrderItemCommand> items, String currency) {

    public record OrderItemCommand(String productId, int quantity, long unitPriceMinor) {}
  }

  record PlaceOrderResult(UUID orderId) {}
}
