package com.rodminjo.commerce.inventory.adapter.out.persistence.jpa;

import com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.entity.InventoryReservationJpaEntity;
import com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.entity.ReservationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationJpaRepository
    extends JpaRepository<InventoryReservationJpaEntity, Long> {

  List<InventoryReservationJpaEntity> findByOrderIdAndStatus(
      String orderId, ReservationStatus status);
}
