package com.rodminjo.commerce.payment.adapter.out.persistence.jpa;

import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

  List<PaymentJpaEntity> findByOrderId(String orderId);

  boolean existsByIdempotencyKey(String idempotencyKey);

  /**
   * 환불 잔액 원자적 차감. 누적 환불액 + 요청액이 결제액 이하일 때만 1행을 갱신한다. 영향 행 0 = 초과 환불(가드 위반)이므로 상위에서 거부 처리.
   *
   * <p>{@code clearAutomatically}로 갱신 후 1차 캐시를 비워 후속 조회가 최신 잔액을 반영하도록 한다.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE PaymentJpaEntity p SET p.refundedAmountMinor = p.refundedAmountMinor + :amount "
          + "WHERE p.id = :paymentId AND p.refundedAmountMinor + :amount <= p.amountMinor")
  int increaseRefundedAmount(@Param("paymentId") UUID paymentId, @Param("amount") long amount);
}
