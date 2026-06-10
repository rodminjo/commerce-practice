package com.rodminjo.commerce.inventory.adapter.in.messaging;

import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.order.OrderPlaced;
import com.rodminjo.commerce.inventory.application.port.in.ReleaseStockUseCase;
import com.rodminjo.commerce.inventory.application.port.in.ReleaseStockUseCase.ReleaseStockCommand;
import com.rodminjo.commerce.inventory.application.port.in.ReserveStockUseCase;
import com.rodminjo.commerce.inventory.application.port.in.ReserveStockUseCase.ReserveStockCommand;
import com.rodminjo.commerce.inventory.application.port.in.ReserveStockUseCase.ReserveStockCommand.Line;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 인바운드 어댑터: Order 이벤트를 인벤토리 유스케이스 호출로 변환. 각 리스너는 타입별 컨테이너 팩토리를 사용하여 {@code
 * KafkaProtobufDeserializer}가 {@code DynamicMessage} 대신 구체 이벤트 타입을 반환.
 */
@RequiredArgsConstructor
@Component
public class OrderEventsConsumer {

  private final ReserveStockUseCase reserveStockUseCase;
  private final ReleaseStockUseCase releaseStockUseCase;

  @KafkaListener(topics = "order.placed", containerFactory = "orderPlacedListenerContainerFactory")
  public void onOrderPlaced(OrderPlaced event) {
    List<Line> items =
        event.getItemsList().stream()
            .map(item -> new Line(item.getProductId(), item.getQuantity()))
            .toList();
    reserveStockUseCase.reserve(new ReserveStockCommand(event.getOrderId(), items));
  }

  @KafkaListener(
      topics = "order.cancelled",
      containerFactory = "orderCancelledListenerContainerFactory")
  public void onOrderCancelled(OrderCancelled event) {
    releaseStockUseCase.release(new ReleaseStockCommand(event.getOrderId(), event.getReason()));
  }
}
