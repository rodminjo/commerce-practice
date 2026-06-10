package com.rodminjo.commerce.payment.application.service.support;

import com.rodminjo.commerce.payment.application.port.out.SavePaymentPort;
import com.rodminjo.commerce.payment.domain.model.Payment;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SavePaymentPort} 인메모리 테스트 대역. 저장된 {@link Payment} 실 도메인 인스턴스를 기록하여 Mockito 상호작용 검증 대신 저장
 * 상태(state)로 단언.
 */
public class FakeSavePaymentPort implements SavePaymentPort {

  private final List<Payment> saved = new ArrayList<>();

  @Override
  public Payment save(Payment payment) {
    saved.add(payment);
    return payment;
  }

  /** 호출 순서대로 기록된 전체 저장 결제 목록. */
  public List<Payment> saved() {
    return saved;
  }

  /** 단일 저장 결제 반환. 저장 건수가 정확히 1건이 아니면 예외 발생. */
  public Payment single() {
    if (saved.size() != 1) {
      throw new IllegalStateException("expected exactly one saved payment but was " + saved.size());
    }
    return saved.get(0);
  }
}
