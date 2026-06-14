package com.rodminjo.commerce.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.payment.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Nested
  @DisplayName("생성 불변식")
  class Creation {

    @Test
    @DisplayName("of(): amount/currency를 보존한다")
    void of_preservesFields() {
      Money money = Money.of(2000L, "KRW");
      assertThat(money.amountMinor()).isEqualTo(2000L);
      assertThat(money.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("currency가 3글자가 아니면 → DomainException(INVALID_PAYMENT)")
    void invalidCurrency_throws() {
      assertThatThrownBy(() -> Money.of(100L, "WO"))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(PaymentErrorCode.INVALID_PAYMENT);
      assertThatThrownBy(() -> Money.of(100L, "KRWW")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("음수 금액 → DomainException (0은 허용)")
    void negativeAmount_throws() {
      assertThatThrownBy(() -> Money.of(-1L, "KRW")).isInstanceOf(DomainException.class);
      assertThat(Money.of(0L, "KRW").amountMinor()).isZero();
    }
  }

  @Nested
  @DisplayName("산술 — 동일 통화 강제")
  class Arithmetic {

    @Test
    @DisplayName("plus/minus/times: 같은 통화 연산")
    void sameCurrencyArithmetic() {
      assertThat(Money.of(1000L, "KRW").plus(Money.of(500L, "KRW")))
          .isEqualTo(Money.of(1500L, "KRW"));
      assertThat(Money.of(1000L, "KRW").minus(Money.of(400L, "KRW")))
          .isEqualTo(Money.of(600L, "KRW"));
      assertThat(Money.of(500L, "KRW").times(3)).isEqualTo(Money.of(1500L, "KRW"));
    }

    @Test
    @DisplayName("isGreaterThan(): 금액 비교")
    void isGreaterThan_compares() {
      assertThat(Money.of(1000L, "KRW").isGreaterThan(Money.of(999L, "KRW"))).isTrue();
      assertThat(Money.of(1000L, "KRW").isGreaterThan(Money.of(1000L, "KRW"))).isFalse();
    }

    @Test
    @DisplayName("minus()가 음수가 되면 → DomainException")
    void minus_negativeResult_throws() {
      assertThatThrownBy(() -> Money.of(100L, "KRW").minus(Money.of(300L, "KRW")))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("통화가 다른 연산 → DomainException")
    void differentCurrency_throws() {
      Money krw = Money.of(1000L, "KRW");
      Money usd = Money.of(1000L, "USD");
      assertThatThrownBy(() -> krw.plus(usd)).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> krw.isGreaterThan(usd)).isInstanceOf(DomainException.class);
    }
  }
}
