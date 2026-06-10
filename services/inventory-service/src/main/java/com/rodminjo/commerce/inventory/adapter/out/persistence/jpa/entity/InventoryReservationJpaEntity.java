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
 * (주문, 상품) 단위 예약 라인 엔티티. 보상 경로({@code order.cancelled}는 orderId만 포함)에서 정확한 수량 복구를 가능케 하고, 예약 경로에서
 * "이미 예약됨" 멱등성 검사 수단으로 활용.
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
