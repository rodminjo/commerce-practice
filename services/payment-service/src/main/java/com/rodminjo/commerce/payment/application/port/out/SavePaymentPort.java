package com.rodminjo.commerce.payment.application.port.out;

import com.rodminjo.commerce.payment.domain.model.Payment;

public interface SavePaymentPort {

  Payment save(Payment payment);

  /** 결제 비즈니스 멱등: 같은 idempotencyKey 결제가 이미 존재하는지 확인. */
  boolean existsByIdempotencyKey(String idempotencyKey);
}
