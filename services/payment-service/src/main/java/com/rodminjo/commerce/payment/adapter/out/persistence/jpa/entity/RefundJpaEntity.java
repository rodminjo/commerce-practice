package com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity;

import com.rodminjo.commerce.payment.domain.model.Refund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code refunds} 테이블 매핑(읽기 전용 매핑). 단순 dedup/적재 레코드이므로 BaseEntity 감사 컬럼 없이 자체 createdAt만 보유. PK는
 * refundId(=환불 식별자), idempotencyKey는 UNIQUE로 이중 환불 차단.
 *
 * <p>적재는 멱등 게이트({@code INSERT ... ON CONFLICT DO NOTHING}, {@code
 * RefundJpaRepository#insertIfAbsent})로 수행하므로 이 엔티티를 통한 {@code save()}(merge)는 사용하지 않는다. 조회 결과
 * 매핑({@link #toDomain()})에만 쓰인다.
 */
@NoArgsConstructor
@Getter
@Entity
@Table(name = "refunds")
public class RefundJpaEntity {

  @Id
  @Column(length = 64)
  private String refundId;

  @Column(nullable = false, length = 64)
  private String paymentId;

  @Column(nullable = false, length = 64)
  private String orderId;

  @Column(nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 128)
  private String idempotencyKey;

  @Column(nullable = false)
  private Instant createdAt;

  public Refund toDomain() {
    return Refund.reconstitute(
        refundId, paymentId, orderId, amountMinor, idempotencyKey, createdAt);
  }
}
