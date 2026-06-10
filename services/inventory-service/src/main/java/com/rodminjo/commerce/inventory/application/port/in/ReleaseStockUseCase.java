package com.rodminjo.commerce.inventory.application.port.in;

public interface ReleaseStockUseCase {

  void release(ReleaseStockCommand command);

  record ReleaseStockCommand(String orderId, String reason) {}
}
