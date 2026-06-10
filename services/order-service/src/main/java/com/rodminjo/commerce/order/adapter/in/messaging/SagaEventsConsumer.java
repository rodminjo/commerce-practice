package com.rodminjo.commerce.order.adapter.in.messaging;

import com.rodminjo.commerce.events.inventory.InventoryReserved;
import com.rodminjo.commerce.events.payment.PaymentCompleted;
import com.rodminjo.commerce.events.payment.PaymentFailed;
import com.rodminjo.commerce.order.application.port.in.OrderSagaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Saga 오케스트레이터에 이벤트를 전달하는 인바운드 어댑터. 소비 이벤트 타입별 리스너 1개씩 등록하며, 타입 전용 컨테이너 팩토리를 통해 Protobuf 페이로드를 구체
 * 클래스로 역직렬화.
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
