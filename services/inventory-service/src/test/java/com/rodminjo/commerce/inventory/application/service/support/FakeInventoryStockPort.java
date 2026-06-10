package com.rodminjo.commerce.inventory.application.service.support;

import com.rodminjo.commerce.inventory.application.port.out.InventoryStockPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Behavior-true in-memory fake of {@link InventoryStockPort}. Models the atomic conditional UPDATE:
 * {@code reserve} succeeds (returns 1, increments reserved) only while {@code stock - reserved >=
 * qty}, otherwise returns 0; {@code release} returns 1 (decrements reserved, floored at 0) when the
 * product is known, else 0.
 */
public class FakeInventoryStockPort implements InventoryStockPort {

  private final Map<String, int[]> stocks = new HashMap<>(); // productId -> [stock, reserved]

  /** Arranges a product with the given total stock and currently reserved quantity. */
  public void seed(String productId, int stock, int reserved) {
    stocks.put(productId, new int[] {stock, reserved});
  }

  @Override
  public boolean exists(String productId) {
    return stocks.containsKey(productId);
  }

  @Override
  public int reserve(String productId, int quantity) {
    int[] row = stocks.get(productId);
    if (row == null) {
      return 0;
    }
    int available = row[0] - row[1];
    if (available < quantity) {
      return 0;
    }
    row[1] += quantity;
    return 1;
  }

  @Override
  public int release(String productId, int quantity) {
    int[] row = stocks.get(productId);
    if (row == null) {
      return 0;
    }
    row[1] = Math.max(0, row[1] - quantity);
    return 1;
  }

  @Override
  public Optional<InventorySnapshot> find(String productId) {
    int[] row = stocks.get(productId);
    if (row == null) {
      return Optional.empty();
    }
    return Optional.of(new InventorySnapshot(productId, row[0], row[1]));
  }

  public int reserved(String productId) {
    int[] row = stocks.get(productId);
    return row == null ? 0 : row[1];
  }
}
