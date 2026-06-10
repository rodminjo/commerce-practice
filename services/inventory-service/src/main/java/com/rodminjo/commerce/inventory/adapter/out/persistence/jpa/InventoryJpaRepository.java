package com.rodminjo.commerce.inventory.adapter.out.persistence.jpa;

import com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.entity.InventoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, String> {

  /**
   * 원자적 조건부 예약 — 초과판매 방지. 가용 재고({@code stock - reserved >= qty})가 충분할 때만 {@code reserved}를 증가. 성공 시
   * 영향 행 {@code 1}, 조건 불충족(재고 부족) 시 {@code 0} 반환. 별도 잠금 불필요(Week 5에서 비교 예정).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          "UPDATE inventory SET reserved = reserved + :qty, audit_updated_at = now()"
              + " WHERE product_id = :pid AND stock - reserved >= :qty")
  int reserveAtomic(@Param("pid") String productId, @Param("qty") int qty);

  /** 원자적 복구: {@code reserved}를 감소(최솟값 0). 성공 시 영향 행 1. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          "UPDATE inventory SET reserved = reserved - :qty, audit_updated_at = now()"
              + " WHERE product_id = :pid AND reserved >= :qty")
  int releaseAtomic(@Param("pid") String productId, @Param("qty") int qty);
}
