package com.rodminjo.commerce.payment.adapter.out.persistence.jpa;

import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity.RefundJpaEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundJpaRepository extends JpaRepository<RefundJpaEntity, String> {

  Optional<RefundJpaEntity> findByIdempotencyKey(String idempotencyKey);

  /**
   * 멱등 게이트: 환불 레코드를 {@code INSERT ... ON CONFLICT DO NOTHING}으로 선점 적재한다. 동시에 같은 refundId가 들어오면
   * Postgres가 유니크 제약 수준에서 직렬화하여, 패자는 예외/롤백 없이 영향 행 0을 받는다.
   *
   * @return 적재된 행 수(1=신규 선점, 0=이미 존재).
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO refunds "
              + "(refund_id, payment_id, order_id, amount_minor, idempotency_key, created_at) "
              + "VALUES (:refundId, :paymentId, :orderId, :amountMinor, :idempotencyKey, :createdAt) "
              + "ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("refundId") String refundId,
      @Param("paymentId") String paymentId,
      @Param("orderId") String orderId,
      @Param("amountMinor") long amountMinor,
      @Param("idempotencyKey") String idempotencyKey,
      @Param("createdAt") Instant createdAt);

  /** 선점한 환불 레코드 취소(초과 환불 시 게이트 롤백). 영속성 컨텍스트 로딩 없이 직접 삭제. */
  @Modifying
  @Query(value = "DELETE FROM refunds WHERE refund_id = :refundId", nativeQuery = true)
  int deleteByRefundIdNative(@Param("refundId") String refundId);
}
