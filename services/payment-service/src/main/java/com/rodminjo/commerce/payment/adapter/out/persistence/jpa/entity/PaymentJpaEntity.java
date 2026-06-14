package com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity;

import com.rodminjo.commerce.common.infra.persistence.BaseEntity;
import com.rodminjo.commerce.payment.domain.model.Payment;
import com.rodminjo.commerce.payment.domain.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "payments")
public class PaymentJpaEntity extends BaseEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 64)
  private String orderId;

  @Column(nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PaymentStatus status;

  @Column(nullable = false, length = 128)
  private String idempotencyKey;

  // 부분/전체 환불 누적액. 환불 시 RefundPort의 원자적 조건부 UPDATE로만 증가하며 도메인 Payment에는 노출하지 않는다.
  @Column(nullable = false)
  private long refundedAmountMinor;

  public static PaymentJpaEntity fromDomain(Payment payment) {
    PaymentJpaEntity entity = new PaymentJpaEntity();
    entity.id = payment.getId();
    entity.orderId = payment.getOrderId();
    entity.amountMinor = payment.getAmount().amountMinor();
    entity.currency = payment.getAmount().currency();
    entity.status = payment.getStatus();
    entity.idempotencyKey = payment.getIdempotencyKey();
    return entity;
  }

  public Payment toDomain() {
    return Payment.reconstitute(id, orderId, amountMinor, currency, status, idempotencyKey);
  }
}
