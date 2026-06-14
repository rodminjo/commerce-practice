package com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity;

import com.rodminjo.commerce.common.infra.persistence.BaseEntity;
import com.rodminjo.commerce.order.domain.model.Money;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "order_items")
public class OrderItemJpaEntity extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private UUID orderId;

  @Column(nullable = false, length = 64)
  private String productId;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  private long unitPriceMinor;

  public OrderItemJpaEntity(UUID orderId, String productId, int quantity, long unitPriceMinor) {
    this.orderId = orderId;
    this.productId = productId;
    this.quantity = quantity;
    this.unitPriceMinor = unitPriceMinor;
  }

  public static OrderItemJpaEntity fromDomain(UUID orderId, OrderLineItem item) {
    return new OrderItemJpaEntity(
        orderId, item.getProductId(), item.getQuantity(), item.getUnitPrice().amountMinor());
  }

  /** 통화는 주문(헤더) 소유 — order_items엔 통화 컬럼이 없어 주문 통화를 주입해 Money를 복원한다. */
  public OrderLineItem toDomain(String currency) {
    return OrderLineItem.of(productId, quantity, Money.of(unitPriceMinor, currency));
  }
}
