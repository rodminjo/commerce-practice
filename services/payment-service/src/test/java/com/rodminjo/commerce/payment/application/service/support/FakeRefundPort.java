package com.rodminjo.commerce.payment.application.service.support;

import com.rodminjo.commerce.payment.application.port.out.RefundPort;
import com.rodminjo.commerce.payment.domain.model.Payment;
import com.rodminjo.commerce.payment.domain.model.Refund;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link RefundPort} 인메모리 테스트 대역. 결제 잔액 차감을 실제 어댑터(원자적 조건부 UPDATE)와 동일한 가드로 흉내내어 상태로 단언한다. 도메인을
 * mock하지 않고 실 {@link Payment}/{@link Refund} 인스턴스를 사용한다.
 */
public class FakeRefundPort implements RefundPort {

  private final List<Refund> saved = new ArrayList<>();
  private final Map<String, Payment> paymentsByOrderId = new HashMap<>();
  private final Map<UUID, Long> refundedByPaymentId = new HashMap<>();
  private final Map<UUID, Long> amountByPaymentId = new HashMap<>();

  /** 환불 대상 결제 등록(orderId → Payment). 누적 환불액 0으로 시작. */
  public void registerPayment(Payment payment) {
    paymentsByOrderId.put(payment.getOrderId(), payment);
    refundedByPaymentId.put(payment.getId(), 0L);
    amountByPaymentId.put(payment.getId(), payment.getAmountMinor());
  }

  @Override
  public Optional<Refund> findByIdempotencyKey(String idempotencyKey) {
    return saved.stream().filter(r -> r.getIdempotencyKey().equals(idempotencyKey)).findFirst();
  }

  @Override
  public Optional<Payment> findPaymentByOrderId(String orderId) {
    return Optional.ofNullable(paymentsByOrderId.get(orderId));
  }

  @Override
  public int increaseRefundedAmount(UUID paymentId, long amountMinor) {
    Long refunded = refundedByPaymentId.get(paymentId);
    Long total = amountByPaymentId.get(paymentId);
    if (refunded == null || total == null) {
      return 0;
    }
    if (refunded + amountMinor > total) {
      return 0;
    }
    refundedByPaymentId.put(paymentId, refunded + amountMinor);
    return 1;
  }

  @Override
  public int insertIfAbsent(Refund refund) {
    // ON CONFLICT DO NOTHING 흉내: 같은 refundId가 이미 있으면 0(미적재), 없으면 추가 후 1.
    boolean exists = saved.stream().anyMatch(r -> r.getRefundId().equals(refund.getRefundId()));
    if (exists) {
      return 0;
    }
    saved.add(refund);
    return 1;
  }

  @Override
  public void deleteByRefundId(String refundId) {
    saved.removeIf(r -> r.getRefundId().equals(refundId));
  }

  public List<Refund> saved() {
    return saved;
  }

  public long refundedAmount(UUID paymentId) {
    return refundedByPaymentId.getOrDefault(paymentId, 0L);
  }
}
