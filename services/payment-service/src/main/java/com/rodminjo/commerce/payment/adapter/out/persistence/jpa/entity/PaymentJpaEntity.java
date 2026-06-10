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

  public static PaymentJpaEntity fromDomain(Payment payment) {
    PaymentJpaEntity entity = new PaymentJpaEntity();
    entity.id = payment.getId();
    entity.orderId = payment.getOrderId();
    entity.amountMinor = payment.getAmountMinor();
    entity.currency = payment.getCurrency();
    entity.status = payment.getStatus();
    entity.idempotencyKey = payment.getIdempotencyKey();
    return entity;
  }

  public Payment toDomain() {
    return Payment.reconstitute(id, orderId, amountMinor, currency, status, idempotencyKey);
  }
}
