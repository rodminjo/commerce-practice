package com.rodminjo.commerce.payment.domain.model;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.payment.domain.PaymentErrorCode;
import java.time.Instant;
import lombok.Getter;

/**
 * Refund 애그리거트. 단일 결제(paymentId)에 대한 1회 환불 적재 레코드. 부분/전체 환불을 지원하며 멱등성 키(=refundId)로 이중 환불을 차단한다. 누적
 * 환불 가능 금액 가드는 {@link Payment} 잔액에 대한 원자적 조건부 UPDATE로 영속성 계층에서 수행한다.
 */
@Getter
public class Refund {

  private final String refundId;
  private final String paymentId;
  private final String orderId;
  private final Money amount;
  private final String idempotencyKey;
  private final Instant createdAt;

  private Refund(
      String refundId,
      String paymentId,
      String orderId,
      Money amount,
      String idempotencyKey,
      Instant createdAt) {
    this.refundId = refundId;
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.amount = amount;
    this.idempotencyKey = idempotencyKey;
    this.createdAt = createdAt;
  }

  public static Refund create(
      String refundId,
      String paymentId,
      String orderId,
      long amountMinor,
      String currency,
      String idempotencyKey,
      Instant createdAt) {
    if (refundId == null || refundId.isBlank()) {
      throw new DomainException(PaymentErrorCode.INVALID_REFUND, "refundId는 비어 있을 수 없습니다");
    }
    if (paymentId == null || paymentId.isBlank()) {
      throw new DomainException(PaymentErrorCode.INVALID_REFUND, "paymentId는 비어 있을 수 없습니다");
    }
    if (orderId == null || orderId.isBlank()) {
      throw new DomainException(PaymentErrorCode.INVALID_REFUND, "orderId는 비어 있을 수 없습니다");
    }
    if (amountMinor <= 0) { // 환불액은 0 초과(Money는 0 허용이므로 루트가 강한 규칙을 책임)
      throw new DomainException(PaymentErrorCode.INVALID_REFUND, "amount는 0보다 커야 합니다");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new DomainException(PaymentErrorCode.INVALID_REFUND, "idempotencyKey는 비어 있을 수 없습니다");
    }
    Money amount = Money.of(amountMinor, currency); // currency 3글자 검증을 Money가 담당
    return new Refund(refundId, paymentId, orderId, amount, idempotencyKey, createdAt);
  }

  public static Refund reconstitute(
      String refundId,
      String paymentId,
      String orderId,
      long amountMinor,
      String currency,
      String idempotencyKey,
      Instant createdAt) {
    return new Refund(
        refundId, paymentId, orderId, Money.of(amountMinor, currency), idempotencyKey, createdAt);
  }
}
