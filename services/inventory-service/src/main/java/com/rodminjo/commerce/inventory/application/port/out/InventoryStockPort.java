package com.rodminjo.commerce.inventory.application.port.out;

import java.util.Optional;

/**
 * Stock write/read seam. {@link #reserve} and {@link #release} are the atomic conditional UPDATEs
 * (the oversell guard) and return the number of affected rows — {@code 0} means the condition
 * failed (insufficient stock / nothing to release).
 */
public interface InventoryStockPort {

  boolean exists(String productId);

  int reserve(String productId, int quantity);

  int release(String productId, int quantity);

  Optional<InventorySnapshot> find(String productId);

  record InventorySnapshot(String productId, int stock, int reserved) {

    public int availableQty() {
      return stock - reserved;
    }
  }
}
