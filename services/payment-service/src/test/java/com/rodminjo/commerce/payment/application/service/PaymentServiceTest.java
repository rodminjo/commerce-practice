package com.rodminjo.commerce.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.events.payment.PaymentCompleted;
import com.rodminjo.commerce.events.payment.PaymentFailed;
import com.rodminjo.commerce.payment.application.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import com.rodminjo.commerce.payment.application.service.support.FakeOutboxAppender;
import com.rodminjo.commerce.payment.application.service.support.FakeOutboxAppender.Appended;
import com.rodminjo.commerce.payment.application.service.support.FakeSavePaymentPort;
import com.rodminjo.commerce.payment.config.PaymentSimulationProperties;
import com.rodminjo.commerce.payment.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

  private static final UUID PAYMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");

  private final FakeSavePaymentPort savePaymentPort = new FakeSavePaymentPort();
  private final FakeOutboxAppender outboxAppender = new FakeOutboxAppender();
  private final PaymentSimulationProperties simulation = new PaymentSimulationProperties();

  private PaymentService service;

  @BeforeEach
  void setUp() {
    service =
        new PaymentService(
            savePaymentPort, outboxAppender, () -> PAYMENT_ID, () -> FIXED_NOW, simulation);
  }

  private static ProcessPaymentCommand command() {
    return new ProcessPaymentCommand("order-1", 2000L, "KRW", "order-1");
  }

  @Nested
  @DisplayName("결제 성공 경로")
  class 결제성공 {

    @Test
    @DisplayName("기본: 결제 성공 → COMPLETED 저장 + payment.completed 적재")
    void successPath() {
      service.process(command());

      assertThat(savePaymentPort.single().getStatus()).isEqualTo(PaymentStatus.COMPLETED);

      assertThat(outboxAppender.appended()).hasSize(1);
      Appended appended = outboxAppender.appended().get(0);
      assertThat(appended.aggregateType()).isEqualTo("Payment");
      assertThat(appended.aggregateId()).isEqualTo("order-1");
      assertThat(appended.topic()).isEqualTo("payment.completed");
      assertThat(appended.partitionKey()).isEqualTo("order-1");
      assertThat(appended.event()).isInstanceOf(PaymentCompleted.class);
      PaymentCompleted event = (PaymentCompleted) appended.event();
      assertThat(event.getOrderId()).isEqualTo("order-1");
      assertThat(event.getPaymentId()).isEqualTo(PAYMENT_ID.toString());
      assertThat(event.getAmountMinor()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("실패 금액과 다르면 성공 처리된다")
    void failInjectionDoesNotMatchOtherAmounts() {
      simulation.setFailForAmount(999L);

      service.process(command());

      assertThat(outboxAppender.appended()).hasSize(1);
      Appended appended = outboxAppender.appended().get(0);
      assertThat(appended.topic()).isEqualTo("payment.completed");
      assertThat(appended.aggregateType()).isEqualTo("Payment");
      assertThat(appended.aggregateId()).isEqualTo("order-1");
      assertThat(appended.partitionKey()).isEqualTo("order-1");
      assertThat(appended.event()).isInstanceOf(PaymentCompleted.class);
    }
  }

  @Nested
  @DisplayName("결제 실패 주입 경로")
  class 결제실패주입 {

    @Test
    @DisplayName("실패 주입: 해당 금액 → FAILED 저장 + payment.failed 적재")
    void failureInjected() {
      simulation.setFailForAmount(2000L);

      service.process(command());

      assertThat(savePaymentPort.single().getStatus()).isEqualTo(PaymentStatus.FAILED);

      assertThat(outboxAppender.appended()).hasSize(1);
      Appended appended = outboxAppender.appended().get(0);
      assertThat(appended.aggregateType()).isEqualTo("Payment");
      assertThat(appended.aggregateId()).isEqualTo("order-1");
      assertThat(appended.topic()).isEqualTo("payment.failed");
      assertThat(appended.partitionKey()).isEqualTo("order-1");
      assertThat(appended.event()).isInstanceOf(PaymentFailed.class);
      assertThat(((PaymentFailed) appended.event()).getReason()).isNotBlank();
    }
  }
}
