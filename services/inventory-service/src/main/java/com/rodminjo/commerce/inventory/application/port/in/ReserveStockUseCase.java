package com.rodminjo.commerce.inventory.application.port.in;

import java.util.List;

public interface ReserveStockUseCase {

  void reserve(ReserveStockCommand command);

  record ReserveStockCommand(String orderId, List<Line> items) {

    public record Line(String productId, int quantity) {}
  }
}
