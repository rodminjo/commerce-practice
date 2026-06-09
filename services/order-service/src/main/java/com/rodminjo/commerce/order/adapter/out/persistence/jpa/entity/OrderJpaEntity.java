package com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity;

import com.rodminjo.commerce.common.infra.persistence.BaseEntity;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "orders")
public class OrderJpaEntity extends BaseEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 64)
  private String customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status;

  @Column(nullable = false)
  private long totalAmountMinor;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public static OrderJpaEntity fromDomain(Order order) {
    OrderJpaEntity entity = new OrderJpaEntity();
    entity.id = order.getId();
    entity.customerId = order.getCustomerId();
    entity.status = order.getStatus();
    entity.totalAmountMinor = order.getTotalAmountMinor();
    entity.currency = order.getCurrency();
    entity.createdAt = order.getCreatedAt();
    return entity;
  }

  public Order toDomain(List<OrderItemJpaEntity> items) {
    List<OrderLineItem> domainItems = items.stream().map(OrderItemJpaEntity::toDomain).toList();
    return Order.reconstitute(
        id, customerId, status, domainItems, totalAmountMinor, currency, createdAt);
  }
}
