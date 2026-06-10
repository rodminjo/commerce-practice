package com.rodminjo.commerce.order.application.port.out;

import com.rodminjo.commerce.order.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

/**
 * Load + status-update seam used by the Saga and the cancel use case. Separate from {@link
 * SaveOrderPort} (which inserts a brand-new order with its items): {@link #update} persists only a
 * status transition on an existing order and must not re-insert the line items.
 */
public interface OrderStateRepositoryPort {

  Optional<Order> findById(UUID id);

  void update(Order order);
}
