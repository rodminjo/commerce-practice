package com.rodminjo.commerce.order.application.service.support;

import com.rodminjo.commerce.order.application.port.out.OrderStateRepositoryPort;
import com.rodminjo.commerce.order.domain.model.Order;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link OrderStateRepositoryPort} test double. Stores orders in a map keyed by id;
 * {@link #update} replaces the stored instance. Use {@link #seed(Order)} to arrange existing state.
 */
public class FakeOrderStateRepository implements OrderStateRepositoryPort {

  private final Map<UUID, Order> store = new HashMap<>();

  /** Arrange helper: pre-load an existing order so the service can find it. */
  public void seed(Order order) {
    store.put(order.getId(), order);
  }

  @Override
  public Optional<Order> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public void update(Order order) {
    store.put(order.getId(), order);
  }
}
