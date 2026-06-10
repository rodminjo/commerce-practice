package com.rodminjo.commerce.order.application.port.in;

/**
 * Inbound port for the Order Saga orchestrator. The messaging adapter ({@code SagaEventsConsumer})
 * depends on this interface, not the concrete service — consistent with the inventory consumer
 * driving {@code ReserveStockUseCase}/{@code ReleaseStockUseCase}. The three handlers form one
 * cohesive state machine, so they live on a single use case rather than being split per event.
 */
public interface OrderSagaUseCase {

  /** Inventory reserved → request payment for the order total. */
  void onInventoryReserved(String orderId);

  /** Payment completed → confirm the order. */
  void onPaymentCompleted(String orderId);

  /** Payment failed → cancel the order and emit compensation (stock release). */
  void onPaymentFailed(String orderId, String reason);
}
