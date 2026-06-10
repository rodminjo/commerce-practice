package com.rodminjo.commerce.inventory.application.port.out;

import java.util.List;

/**
 * Tracks what each order reserved so the compensation path ({@code order.cancelled}, which carries
 * only the orderId) can release exactly those quantities. Doubles as a partial idempotency guard:
 * an order that already has active reservations is not reserved again.
 */
public interface ReservationPort {

  void saveAll(String orderId, List<ReservedLine> lines);

  List<ReservedLine> findActive(String orderId);

  void markReleased(String orderId);

  record ReservedLine(String productId, int quantity) {}
}
