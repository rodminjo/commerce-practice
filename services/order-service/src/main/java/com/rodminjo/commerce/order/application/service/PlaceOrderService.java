package com.rodminjo.commerce.order.application.service;

import com.google.protobuf.Timestamp;
import com.rodminjo.commerce.common.outbox.OutboxAppender;
import com.rodminjo.commerce.events.order.OrderPlaced;
import com.rodminjo.commerce.common.id.IdGenerator;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.application.port.out.SaveOrderPort;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class PlaceOrderService implements PlaceOrderUseCase {

    private final SaveOrderPort saveOrderPort;
    private final OutboxAppender outboxAppender;
    private final IdGenerator<UUID> idGenerator;
    private final ClockHolder clockHolder;

    @Override
    @Transactional
    public PlaceOrderResult place(PlaceOrderCommand command) {
        List<OrderLineItem> items = command.items().stream()
                .map(i -> OrderLineItem.of(i.productId(), i.quantity(), i.unitPriceMinor()))
                .toList();

        Order order = Order.place(idGenerator.newId(), command.customerId(), items, command.currency(), clockHolder.now());
        Order saved = saveOrderPort.save(order);

        OrderPlaced event = buildOrderPlacedEvent(saved);
        outboxAppender.append(
                "Order",
                saved.getId().toString(),
                "order.placed",
                saved.getId().toString(),
                event
        );

        return new PlaceOrderResult(saved.getId());
    }

    private OrderPlaced buildOrderPlacedEvent(Order order) {
        Timestamp occurredAt = Timestamp.newBuilder()
                .setSeconds(order.getCreatedAt().getEpochSecond())
                .setNanos(order.getCreatedAt().getNano())
                .build();

        OrderPlaced.Builder builder = OrderPlaced.newBuilder()
                .setOrderId(order.getId().toString())
                .setCustomerId(order.getCustomerId())
                .setTotalAmountMinor(order.getTotalAmountMinor())
                .setCurrency(order.getCurrency())
                .setOccurredAt(occurredAt);

        for (OrderLineItem item : order.getItems()) {
            com.rodminjo.commerce.events.order.OrderLineItem protoItem =
                    com.rodminjo.commerce.events.order.OrderLineItem.newBuilder()
                            .setProductId(item.getProductId())
                            .setQuantity(item.getQuantity())
                            .setUnitPriceMinor(item.getUnitPriceMinor())
                            .build();
            builder.addItems(protoItem);
        }

        return builder.build();
    }
}
