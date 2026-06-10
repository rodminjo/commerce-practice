package com.rodminjo.commerce.inventory.adapter.out.persistence.jpa;

import com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.entity.InventoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, String> {

  /**
   * Atomic conditional reservation — the oversell guard. Increments {@code reserved} only while
   * enough free stock remains; the returned affected-row count is {@code 1} on success and {@code
   * 0} when {@code stock - reserved < qty}. No separate lock needed (lock comparison ships in Week
   * 5).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          "UPDATE inventory SET reserved = reserved + :qty, audit_updated_at = now()"
              + " WHERE product_id = :pid AND stock - reserved >= :qty")
  int reserveAtomic(@Param("pid") String productId, @Param("qty") int qty);

  /** Atomic release: decrements {@code reserved}, never below 0. Affected rows: 1 on success. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          "UPDATE inventory SET reserved = reserved - :qty, audit_updated_at = now()"
              + " WHERE product_id = :pid AND reserved >= :qty")
  int releaseAtomic(@Param("pid") String productId, @Param("qty") int qty);
}
