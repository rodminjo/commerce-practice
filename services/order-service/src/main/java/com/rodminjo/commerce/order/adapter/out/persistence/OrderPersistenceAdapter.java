package com.rodminjo.commerce.order.adapter.out.persistence;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderItemJpaRepository;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderJpaRepository;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderItemJpaEntity;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.rodminjo.commerce.order.application.port.out.OrderStateRepositoryPort;
import com.rodminjo.commerce.order.application.port.out.SaveOrderPort;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import com.rodminjo.commerce.order.domain.model.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class OrderPersistenceAdapter implements SaveOrderPort, OrderStateRepositoryPort {

  private final OrderJpaRepository orderJpaRepository;
  private final OrderItemJpaRepository orderItemJpaRepository;

  @Override
  public Order save(Order order) {
    orderJpaRepository.save(OrderJpaEntity.fromDomain(order));
    List<OrderItemJpaEntity> items =
        order.getItems().stream()
            .map(item -> OrderItemJpaEntity.fromDomain(order.getId(), item))
            .toList();

    orderItemJpaRepository.saveAll(items);
    return order;
  }

  @Override
  public Optional<Order> findById(UUID id) {
    return orderJpaRepository
        .findById(id)
        .map(entity -> entity.toDomain(orderItemJpaRepository.findByOrderId(id)));
  }

  /** Persists a status transition only — never touches the (immutable) line items. */
  @Override
  public void update(Order order) {
    OrderJpaEntity entity =
        orderJpaRepository
            .findById(order.getId())
            .orElseThrow(() -> new DomainException(OrderErrorCode.ORDER_NOT_FOUND));
    entity.changeStatus(order.getStatus());
    orderJpaRepository.save(entity);
  }
}
