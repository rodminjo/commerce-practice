package com.rodminjo.commerce.order.application.port.in;

import java.util.UUID;

public interface CancelOrderUseCase {

  void cancel(CancelOrderCommand command);

  record CancelOrderCommand(UUID orderId, String reason) {}
}
