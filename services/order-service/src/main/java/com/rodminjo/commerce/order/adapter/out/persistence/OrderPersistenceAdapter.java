package com.rodminjo.commerce.order.adapter.out.persistence;

import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderItemJpaRepository;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderJpaRepository;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderItemJpaEntity;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.rodminjo.commerce.order.application.port.out.SaveOrderPort;
import com.rodminjo.commerce.order.domain.model.Order;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderPersistenceAdapter implements SaveOrderPort {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;

    public OrderPersistenceAdapter(OrderJpaRepository orderJpaRepository,
                                   OrderItemJpaRepository orderItemJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderItemJpaRepository = orderItemJpaRepository;
    }

    @Override
    public Order save(Order order) {
        orderJpaRepository.save(OrderJpaEntity.fromDomain(order));
        List<OrderItemJpaEntity> items = order.getItems().stream()
                .map(item -> OrderItemJpaEntity.fromDomain(order.getId(), item))
                .toList();
        orderItemJpaRepository.saveAll(items);
        return order;
    }
}
