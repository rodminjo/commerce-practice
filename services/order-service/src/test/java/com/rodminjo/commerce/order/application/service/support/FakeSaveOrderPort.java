package com.rodminjo.commerce.order.application.service.support;

import com.rodminjo.commerce.order.application.port.out.SaveOrderPort;
import com.rodminjo.commerce.order.domain.model.Order;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link SaveOrderPort} test double. Records each saved order and returns it unchanged
 * (mirroring the real adapter, which persists and returns the same aggregate).
 */
public class FakeSaveOrderPort implements SaveOrderPort {

  private final List<Order> saved = new ArrayList<>();

  @Override
  public Order save(Order order) {
    saved.add(order);
    return order;
  }

  /** All orders passed to {@link #save}, in call order. */
  public List<Order> saved() {
    return saved;
  }
}
