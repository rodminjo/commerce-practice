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
 * Inbound adapter: turns Order events into inventory use-case calls. Each listener uses a
 * type-specific container factory so the Protobuf deserializer yields the concrete event type
 * rather than a {@code DynamicMessage}.
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
