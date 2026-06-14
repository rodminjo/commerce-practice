package com.rodminjo.commerce.order.domain.model;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import lombok.Getter;

@Getter
public final class OrderLineItem {

  private final String productId;
  private final int quantity;
  private final Money unitPrice;

  private OrderLineItem(String productId, int quantity, Money unitPrice) {
    this.productId = productId;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  public static OrderLineItem of(String productId, int quantity, Money unitPrice) {
    if (productId == null || productId.isBlank()) {
      throw new DomainException(OrderErrorCode.INVALID_ORDER, "productId는 비어 있을 수 없습니다");
    }
    if (quantity <= 0) {
      throw new DomainException(OrderErrorCode.INVALID_ORDER, "quantity는 0보다 커야 합니다");
    }
    return new OrderLineItem(productId, quantity, unitPrice); // 음수가 단가는 Money가 거부
  }

  public Money lineTotal() {
    return unitPrice.times(quantity);
  }
}
