package com.rodminjo.commerce.order.adapter.in.messaging;

import com.rodminjo.commerce.events.inventory.InventoryReserved;
import com.rodminjo.commerce.events.payment.PaymentCompleted;
import com.rodminjo.commerce.events.payment.PaymentFailed;
import com.rodminjo.commerce.order.application.port.in.OrderSagaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter feeding the Saga orchestrator. One listener per consumed event type, each bound
 * to a type-specific container factory so the Protobuf payload deserializes to the concrete class.
 */
@RequiredArgsConstructor
@Component
public class SagaEventsConsumer {

  private final OrderSagaUseCase orderSaga;

  @KafkaListener(
      topics = "inventory.reserved",
      containerFactory = "inventoryReservedListenerContainerFactory")
  public void onInventoryReserved(InventoryReserved event) {
    orderSaga.onInventoryReserved(event.getOrderId());
  }

  @KafkaListener(
      topics = "payment.completed",
      containerFactory = "paymentCompletedListenerContainerFactory")
  public void onPaymentCompleted(PaymentCompleted event) {
    orderSaga.onPaymentCompleted(event.getOrderId());
  }

  @KafkaListener(
      topics = "payment.failed",
      containerFactory = "paymentFailedListenerContainerFactory")
  public void onPaymentFailed(PaymentFailed event) {
    orderSaga.onPaymentFailed(event.getOrderId(), event.getReason());
  }
}
