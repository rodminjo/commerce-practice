package com.rodminjo.commerce.inventory.adapter.out.persistence;

import com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.InventoryJpaRepository;
import com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.InventoryReservationJpaRepository;
import com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.entity.InventoryReservationJpaEntity;
import com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.entity.ReservationStatus;
import com.rodminjo.commerce.inventory.application.port.out.InventoryStockPort;
import com.rodminjo.commerce.inventory.application.port.out.ReservationPort;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class InventoryPersistenceAdapter implements InventoryStockPort, ReservationPort {

  private final InventoryJpaRepository inventoryJpaRepository;
  private final InventoryReservationJpaRepository reservationJpaRepository;

  @Override
  public boolean exists(String productId) {
    return inventoryJpaRepository.existsById(productId);
  }

  @Override
  public int reserve(String productId, int quantity) {
    return inventoryJpaRepository.reserveAtomic(productId, quantity);
  }

  @Override
  public int release(String productId, int quantity) {
    return inventoryJpaRepository.releaseAtomic(productId, quantity);
  }

  @Override
  public Optional<InventorySnapshot> find(String productId) {
    return inventoryJpaRepository
        .findById(productId)
        .map(e -> new InventorySnapshot(e.getProductId(), e.getStock(), e.getReserved()));
  }

  @Override
  public void saveAll(String orderId, List<ReservedLine> lines) {
    List<InventoryReservationJpaEntity> entities =
        lines.stream()
            .map(
                line ->
                    new InventoryReservationJpaEntity(orderId, line.productId(), line.quantity()))
            .toList();
    reservationJpaRepository.saveAll(entities);
  }

  @Override
  public List<ReservedLine> findActive(String orderId) {
    return reservationJpaRepository
        .findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED)
        .stream()
        .map(e -> new ReservedLine(e.getProductId(), e.getQuantity()))
        .toList();
  }

  @Override
  public void markReleased(String orderId) {
    List<InventoryReservationJpaEntity> active =
        reservationJpaRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);
    active.forEach(InventoryReservationJpaEntity::markReleased);
    reservationJpaRepository.saveAll(active);
  }
}
