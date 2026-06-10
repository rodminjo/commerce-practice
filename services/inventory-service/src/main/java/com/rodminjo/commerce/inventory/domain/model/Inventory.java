package com.rodminjo.commerce.inventory.domain.model;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.inventory.domain.InventoryErrorCode;
import lombok.Getter;

/**
 * 단일 상품 재고 애그리게이트. 불변 값 스타일: {@link #reserve(int)}와 {@link #release(int)}는 변이 없이 새 인스턴스 반환. 도메인이
 * 초과판매 불변식(가용 수량 초과 예약 불가)을 인코딩하며, 영속 어댑터는 동일 규칙을 조건부 UPDATE로 DB에서 원자적으로 강제. 도메인 레이어에 규칙을 보유함으로써 DB
 * 없이 단위 테스트 가능하고 SQL이 수호하는 불변식을 문서화.
 */
@Getter
public final class Inventory {

  private final String productId;
  private final int stock;
  private final int reserved;

  private Inventory(String productId, int stock, int reserved) {
    this.productId = productId;
    this.stock = stock;
    this.reserved = reserved;
  }

  public static Inventory of(String productId, int stock, int reserved) {
    if (productId == null || productId.isBlank()) {
      throw new DomainException(InventoryErrorCode.INVALID_INVENTORY, "productId는 비어 있을 수 없습니다");
    }
    if (stock < 0) {
      throw new DomainException(InventoryErrorCode.INVALID_INVENTORY, "stock은 0 이상이어야 합니다");
    }
    if (reserved < 0 || reserved > stock) {
      throw new DomainException(
          InventoryErrorCode.INVALID_INVENTORY, "reserved는 0 이상 stock 이하여야 합니다");
    }
    return new Inventory(productId, stock, reserved);
  }

  public int availableQty() {
    return stock - reserved;
  }

  public Inventory reserve(int quantity) {
    requirePositive(quantity);
    if (availableQty() < quantity) {
      throw new DomainException(InventoryErrorCode.INSUFFICIENT_STOCK);
    }
    return new Inventory(productId, stock, reserved + quantity);
  }

  public Inventory release(int quantity) {
    requirePositive(quantity);
    if (reserved < quantity) {
      throw new DomainException(InventoryErrorCode.INVALID_INVENTORY, "예약 수량보다 많이 복구할 수 없습니다");
    }
    return new Inventory(productId, stock, reserved - quantity);
  }

  private static void requirePositive(int quantity) {
    if (quantity <= 0) {
      throw new DomainException(InventoryErrorCode.INVALID_INVENTORY, "수량은 0보다 커야 합니다");
    }
  }
}
