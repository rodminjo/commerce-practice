package com.rodminjo.commerce.payment.adapter.out.persistence;

import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.PaymentJpaRepository;
import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.RefundJpaRepository;
import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity.RefundJpaEntity;
import com.rodminjo.commerce.payment.application.port.out.RefundPort;
import com.rodminjo.commerce.payment.domain.model.Payment;
import com.rodminjo.commerce.payment.domain.model.Refund;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RefundPersistenceAdapter implements RefundPort {

  private final RefundJpaRepository refundJpaRepository;
  private final PaymentJpaRepository paymentJpaRepository;

  @Override
  public Optional<Refund> findByIdempotencyKey(String idempotencyKey) {
    return refundJpaRepository.findByIdempotencyKey(idempotencyKey).map(RefundJpaEntity::toDomain);
  }

  @Override
  public Optional<Payment> findPaymentByOrderId(String orderId) {
    // 한 주문에 결제 행이 여럿(예: 실패 후 재시도)일 수 있으므로 COMPLETED 결제만 환불 대상으로 선택.
    return paymentJpaRepository.findByOrderId(orderId).stream()
        .map(PaymentJpaEntity::toDomain)
        .filter(Payment::isComplete)
        .findFirst();
  }

  @Override
  public int increaseRefundedAmount(UUID paymentId, long amountMinor) {
    return paymentJpaRepository.increaseRefundedAmount(paymentId, amountMinor);
  }

  @Override
  public int insertIfAbsent(Refund refund) {
    return refundJpaRepository.insertIfAbsent(
        refund.getRefundId(),
        refund.getPaymentId(),
        refund.getOrderId(),
        refund.getAmountMinor(),
        refund.getIdempotencyKey(),
        refund.getCreatedAt());
  }

  @Override
  public void deleteByRefundId(String refundId) {
    refundJpaRepository.deleteByRefundIdNative(refundId);
  }
}
