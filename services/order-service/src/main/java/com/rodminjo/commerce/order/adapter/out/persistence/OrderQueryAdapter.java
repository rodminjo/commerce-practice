package com.rodminjo.commerce.order.adapter.out.persistence;

import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase.OrderView;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase.OrderView.OrderItemView;
import com.rodminjo.commerce.order.application.port.out.OrderQueryPort;
import com.rodminjo.commerce.order.adapter.out.persistence.mybatis.OrderQueryMapper;
import com.rodminjo.commerce.order.adapter.out.persistence.mybatis.OrderRow;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderQueryAdapter implements OrderQueryPort {

    private final OrderQueryMapper orderQueryMapper;

    public OrderQueryAdapter(OrderQueryMapper orderQueryMapper) {
        this.orderQueryMapper = orderQueryMapper;
    }

    @Override
    public Optional<OrderView> findById(UUID id) {
        OrderRow row = orderQueryMapper.findOrderById(id.toString());
        if (row == null) {
            return Optional.empty();
        }
        List<OrderItemView> items = row.getItems().stream()
                .map(item -> new OrderItemView(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPriceMinor()))
                .toList();
        return Optional.of(new OrderView(
                UUID.fromString(row.getId()),
                OrderStatus.valueOf(row.getStatus()),
                items,
                row.getTotalAmountMinor(),
                row.getCurrency()));
    }
}
