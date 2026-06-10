package com.rodminjo.commerce.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.payment.domain.model.Payment;
import com.rodminjo.commerce.payment.domain.model.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentDomainTest {

  private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private static Payment requested() {
    return Payment.request(ID, "order-1", 2000L, "KRW", "order-1");
  }

  @Nested
  @DisplayName("결제 생성 (request)")
  class 결제생성 {

    @Test
    @DisplayName("request(): REQUESTED 상태로 생성된다")
    void requestCreatesRequested() {
      assertThat(requested().getStatus()).isEqualTo(PaymentStatus.REQUESTED);
    }

    @Test
    @DisplayName("amount가 0 이하면 생성 거부")
    void invalidAmountRejected() {
      assertThatThrownBy(() -> Payment.request(ID, "order-1", 0L, "KRW", "order-1"))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(PaymentErrorCode.INVALID_PAYMENT);
    }
  }

  @Nested
  @DisplayName("결제 상태 전이")
  class 상태전이 {

    @Test
    @DisplayName("complete(): REQUESTED → COMPLETED")
    void complete() {
      Payment payment = requested();
      payment.complete();
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("fail(): REQUESTED → FAILED")
    void fail() {
      Payment payment = requested();
      payment.fail();
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("이미 COMPLETED면 다시 전이할 수 없다 (INVALID_PAYMENT_STATE)")
    void terminalStateRejectsTransition() {
      Payment payment = requested();
      payment.complete();

      assertThatThrownBy(payment::fail)
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATE);
    }
  }
}
