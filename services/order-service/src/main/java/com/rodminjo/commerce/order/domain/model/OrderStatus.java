package com.rodminjo.commerce.order.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
  PENDING,
  CONFIRMED,
  COMPLETED,
  CANCELLED,
  REFUNDED;

  private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          PENDING, EnumSet.of(CONFIRMED, CANCELLED),
          CONFIRMED, EnumSet.of(COMPLETED, CANCELLED, REFUNDED),
          COMPLETED, EnumSet.of(REFUNDED),
          CANCELLED, EnumSet.noneOf(OrderStatus.class),
          REFUNDED, EnumSet.noneOf(OrderStatus.class));

  public boolean canTransitionTo(OrderStatus target) {
    return ALLOWED_TRANSITIONS.get(this).contains(target);
  }
}
