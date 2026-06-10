package com.rodminjo.commerce.inventory.domain.model;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.inventory.domain.InventoryErrorCode;
import lombok.Getter;

/**
 * Inventory aggregate for a single product. Immutable value-style: {@link #reserve(int)} and {@link
 * #release(int)} return a new instance, never mutate. The domain encodes the oversell invariant
 * (you cannot reserve beyond the available quantity); the persistence adapter enforces the same
 * rule atomically in the database via a conditional UPDATE. Keeping the rule here makes it
 * unit-testable without a database and documents the invariant the SQL guards.
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
