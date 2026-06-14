package com.rodminjo.commerce.payment.domain.model;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.payment.domain.PaymentErrorCode;
import java.util.UUID;
import lombok.Getter;

/**
 * Payment 애그리거트. REQUESTED 상태로 시작하여 COMPLETED 또는 FAILED 종단 상태로 1회 전이. 멱등성 키(Week 3 기준 orderId)를 저장;
 * 키 기반 중복 제거는 Week 4 구현 예정.
 */
@Getter
public class Payment {

  private final UUID id;
  private final String orderId;
  private final long amountMinor;
  private final String currency;
  private PaymentStatus status;
  private final String idempotencyKey;

  private Payment(
      UUID id,
      String orderId,
      long amountMinor,
      String currency,
      PaymentStatus status,
      String idempotencyKey) {
    this.id = id;
    this.orderId = orderId;
    this.amountMinor = amountMinor;
    this.currency = currency;
    this.status = status;
    this.idempotencyKey = idempotencyKey;
  }

  public static Payment request(
      UUID id, String orderId, long amountMinor, String currency, String idempotencyKey) {
    if (id == null) {
      throw new DomainException(PaymentErrorCode.INVALID_PAYMENT, "id는 필수입니다");
    }
    if (orderId == null || orderId.isBlank()) {
      throw new DomainException(PaymentErrorCode.INVALID_PAYMENT, "orderId는 비어 있을 수 없습니다");
    }
    if (amountMinor <= 0) {
      throw new DomainException(PaymentErrorCode.INVALID_PAYMENT, "amount는 0보다 커야 합니다");
    }
    if (currency == null || currency.length() != 3) {
      throw new DomainException(PaymentErrorCode.INVALID_PAYMENT, "currency는 3글자여야 합니다");
    }
    return new Payment(id, orderId, amountMinor, currency, PaymentStatus.REQUESTED, idempotencyKey);
  }

  public static Payment reconstitute(
      UUID id,
      String orderId,
      long amountMinor,
      String currency,
      PaymentStatus status,
      String idempotencyKey) {
    return new Payment(id, orderId, amountMinor, currency, status, idempotencyKey);
  }

  public boolean isComplete() {
    return this.status == PaymentStatus.COMPLETED;
  }

  public void complete() {
    requireRequested();
    this.status = PaymentStatus.COMPLETED;
  }

  public void fail() {
    requireRequested();
    this.status = PaymentStatus.FAILED;
  }

  private void requireRequested() {
    if (status != PaymentStatus.REQUESTED) {
      throw new DomainException(PaymentErrorCode.INVALID_PAYMENT_STATE);
    }
  }
}
