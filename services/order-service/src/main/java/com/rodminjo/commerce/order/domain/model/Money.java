package com.rodminjo.commerce.order.domain.model;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.domain.OrderErrorCode;

/**
 * 금액 값 객체(불변). 최소 화폐 단위 정수({@code amountMinor})와 ISO-4217 통화 코드({@code currency})를 한 쌍으로 묶어 "금액은 통화
 * 없이 의미가 없다"를 타입으로 강제한다.
 *
 * <p>불변식은 <b>느슨한 최소 규칙</b>(통화 3글자 · 음수 금지)만 갖는다. 맥락별 강한 규칙(예: 결제액은 {@code > 0})은 각 애그리거트가
 * 책임진다(Money는 {@code 0}을 허용). 산술({@link #plus}/{@link #minus}/비교)은 <b>동일 통화</b>를 강제하여 통화가 다른 금액의
 * 무의미한 연산을 차단한다.
 */
public record Money(long amountMinor, String currency) {

  public Money {
    if (currency == null || currency.length() != 3) {
      throw new DomainException(OrderErrorCode.INVALID_ORDER, "currency는 3글자여야 합니다");
    }
    if (amountMinor < 0) {
      throw new DomainException(OrderErrorCode.INVALID_ORDER, "amount는 0 이상이어야 합니다");
    }
  }

  public static Money of(long amountMinor, String currency) {
    return new Money(amountMinor, currency);
  }

  public static Money zero(String currency) {
    return new Money(0L, currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(amountMinor + other.amountMinor, currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(amountMinor - other.amountMinor, currency); // 음수 결과는 생성자가 거부
  }

  public Money times(int quantity) {
    return new Money(amountMinor * quantity, currency);
  }

  public boolean isGreaterThan(Money other) {
    requireSameCurrency(other);
    return amountMinor > other.amountMinor;
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new DomainException(
          OrderErrorCode.INVALID_ORDER, "통화가 다릅니다: " + currency + " vs " + other.currency);
    }
  }
}
