package com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.entity;

import com.rodminjo.commerce.common.infra.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One reserved line per (order, product). Lets the compensation path release exactly what an order
 * reserved (the {@code order.cancelled} event carries only the orderId), and gives the reserve path
 * a cheap "already reserved?" idempotency check.
 */
@NoArgsConstructor
@Getter
@Entity
@Table(name = "inventory_reservation")
public class InventoryReservationJpaEntity extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String orderId;

  @Column(nullable = false, length = 64)
  private String productId;

  @Column(nullable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ReservationStatus status;

  public InventoryReservationJpaEntity(String orderId, String productId, int quantity) {
    this.orderId = orderId;
    this.productId = productId;
    this.quantity = quantity;
    this.status = ReservationStatus.RESERVED;
  }

  public void markReleased() {
    this.status = ReservationStatus.RELEASED;
  }
}
