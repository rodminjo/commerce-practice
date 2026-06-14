package com.rodminjo.commerce.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.domain.model.Money;
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
      Money money = Money.of(1234L, "KRW");
      assertThat(money.amountMinor()).isEqualTo(1234L);
      assertThat(money.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("zero(): 0원 금액")
    void zero_isZeroAmount() {
      assertThat(Money.zero("USD").amountMinor()).isZero();
    }

    @Test
    @DisplayName("currency가 3글자가 아니면 → DomainException(INVALID_ORDER)")
    void invalidCurrency_throws() {
      assertThatThrownBy(() -> Money.of(100L, "KR"))
          .isInstanceOf(DomainException.class)
          .satisfies(
              ex ->
                  assertThat(((DomainException) ex).errorCode().code()).isEqualTo("INVALID_ORDER"));
      assertThatThrownBy(() -> Money.of(100L, null)).isInstanceOf(DomainException.class);
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
    @DisplayName("plus(): 같은 통화끼리 합산")
    void plus_sameCurrency() {
      assertThat(Money.of(1000L, "KRW").plus(Money.of(500L, "KRW")))
          .isEqualTo(Money.of(1500L, "KRW"));
    }

    @Test
    @DisplayName("minus(): 같은 통화끼리 차감")
    void minus_sameCurrency() {
      assertThat(Money.of(1000L, "KRW").minus(Money.of(400L, "KRW")))
          .isEqualTo(Money.of(600L, "KRW"));
    }

    @Test
    @DisplayName("times(): 수량 곱")
    void times_multipliesAmount() {
      assertThat(Money.of(500L, "KRW").times(3)).isEqualTo(Money.of(1500L, "KRW"));
    }

    @Test
    @DisplayName("minus()가 음수가 되면 → DomainException")
    void minus_negativeResult_throws() {
      assertThatThrownBy(() -> Money.of(100L, "KRW").minus(Money.of(300L, "KRW")))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("isGreaterThan(): 금액 비교")
    void isGreaterThan_compares() {
      assertThat(Money.of(1000L, "KRW").isGreaterThan(Money.of(999L, "KRW"))).isTrue();
      assertThat(Money.of(1000L, "KRW").isGreaterThan(Money.of(1000L, "KRW"))).isFalse();
    }

    @Test
    @DisplayName("통화가 다른 plus/minus/비교 → DomainException")
    void differentCurrency_throws() {
      Money krw = Money.of(1000L, "KRW");
      Money usd = Money.of(1000L, "USD");
      assertThatThrownBy(() -> krw.plus(usd)).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> krw.minus(usd)).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> krw.isGreaterThan(usd)).isInstanceOf(DomainException.class);
    }
  }
}
