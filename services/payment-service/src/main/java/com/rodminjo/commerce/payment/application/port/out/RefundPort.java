package com.rodminjo.commerce.payment.application.port.out;

import com.rodminjo.commerce.payment.domain.model.Payment;
import com.rodminjo.commerce.payment.domain.model.Refund;
import java.util.Optional;
import java.util.UUID;

/** 환불 영속성 포트. 멱등 조회/적재와 결제 잔액에 대한 원자적 조건부 차감을 제공. */
public interface RefundPort {

  /** idempotencyKey로 기존 환불 조회. 멱등 재요청 시 기존 결과 재사용. */
  Optional<Refund> findByIdempotencyKey(String idempotencyKey);

  /** orderId로 결제 단건 조회(결제→환불 매핑). */
  Optional<Payment> findPaymentByOrderId(String orderId);

  /**
   * 결제 잔액 원자적 차감. {@code refunded_amount_minor + amount <= amount_minor} 조건을 만족할 때만 1행을 갱신한다.
   *
   * @return 갱신된 행 수. 0이면 초과 환불(가드 위반).
   */
  int increaseRefundedAmount(UUID paymentId, long amountMinor);

  /**
   * 환불 레코드를 멱등 게이트로 선점 적재({@code INSERT ... ON CONFLICT DO NOTHING}). 이미 같은 환불이 존재하면 충돌을 예외 없이
   * 흡수한다.
   *
   * @return 적재된 행 수. 1이면 신규 선점 성공, 0이면 이미 처리된 환불(멱등 재요청).
   */
  int insertIfAbsent(Refund refund);

  /** 선점한 환불 레코드 취소(초과 환불로 잔액 차감에 실패한 경우 게이트 롤백용). */
  void deleteByRefundId(String refundId);
}
