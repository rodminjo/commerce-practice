package com.rodminjo.commerce.payment.adapter.out.persistence;

import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.PaymentJpaRepository;
import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import com.rodminjo.commerce.payment.application.port.out.SavePaymentPort;
import com.rodminjo.commerce.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentPersistenceAdapter implements SavePaymentPort {

  private final PaymentJpaRepository paymentJpaRepository;

  @Override
  public Payment save(Payment payment) {
    paymentJpaRepository.save(PaymentJpaEntity.fromDomain(payment));
    return payment;
  }

  @Override
  public boolean existsByIdempotencyKey(String idempotencyKey) {
    return paymentJpaRepository.existsByIdempotencyKey(idempotencyKey);
  }
}
