package com.rodminjo.commerce.inventory.application.port.out;

import java.util.Optional;

/**
 * 재고 쓰기/읽기 포트. {@link #reserve}와 {@link #release}는 원자적 조건부 UPDATE(초과판매 방지)이며 영향 행 수를 반환. {@code 0}은
 * 조건 불충족(재고 부족 또는 복구 대상 없음)을 의미.
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
