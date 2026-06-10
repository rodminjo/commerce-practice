package com.rodminjo.commerce.inventory.application.service.support;

import com.rodminjo.commerce.inventory.application.port.out.InventoryStockPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link InventoryStockPort}의 동작 충실 인메모리 Fake. 원자적 조건부 UPDATE 모델링: {@code reserve}는 {@code stock -
 * reserved >= qty}일 때만 성공(1 반환, reserved 증가), 아니면 0 반환. {@code release}는 상품이 존재하면 1 반환(reserved 감소,
 * 최솟값 0), 존재하지 않으면 0 반환.
 */
public class FakeInventoryStockPort implements InventoryStockPort {

  private final Map<String, int[]> stocks = new HashMap<>(); // productId -> [stock, reserved]

  /** 지정 총 재고와 현재 예약 수량으로 상품 초기화. */
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
