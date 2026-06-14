package com.rodminjo.commerce.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.events.payment.RefundCompleted;
import com.rodminjo.commerce.events.payment.RefundFailed;
import com.rodminjo.commerce.payment.application.port.in.ProcessRefundUseCase.ProcessRefundCommand;
import com.rodminjo.commerce.payment.application.service.support.FakeOutboxAppender;
import com.rodminjo.commerce.payment.application.service.support.FakeOutboxAppender.Appended;
import com.rodminjo.commerce.payment.application.service.support.FakeRefundPort;
import com.rodminjo.commerce.payment.domain.model.Payment;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefundServiceTest {

  private static final UUID PAYMENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");
  private static final String ORDER_ID = "order-1";

  private final FakeRefundPort refundPort = new FakeRefundPort();
  private final FakeOutboxAppender outboxAppender = new FakeOutboxAppender();

  private RefundService service;

  @BeforeEach
  void setUp() {
    service = new RefundService(refundPort, outboxAppender, () -> FIXED_NOW);
    refundPort.registerPayment(
        Payment.reconstitute(
            PAYMENT_ID,
            ORDER_ID,
            10_000L,
            "KRW",
            com.rodminjo.commerce.payment.domain.model.PaymentStatus.COMPLETED,
            ORDER_ID));
  }

  private ProcessRefundCommand command(String refundId, long amount) {
    return new ProcessRefundCommand(
        ORDER_ID, PAYMENT_ID.toString(), refundId, amount, "customer", refundId);
  }

  @Test
  @DisplayName("부분 환불 성공 → refunds 1행 + refund.completed 적재")
  void partialRefund() {
    service.process(command("refund-1", 3000L));

    assertThat(refundPort.saved()).hasSize(1);
    assertThat(refundPort.refundedAmount(PAYMENT_ID)).isEqualTo(3000L);

    assertThat(outboxAppender.appended()).hasSize(1);
    Appended appended = outboxAppender.appended().get(0);
    assertThat(appended.topic()).isEqualTo("refund.completed");
    assertThat(appended.aggregateType()).isEqualTo("Payment");
    assertThat(appended.aggregateId()).isEqualTo(ORDER_ID);
    assertThat(appended.event()).isInstanceOf(RefundCompleted.class);
    RefundCompleted event = (RefundCompleted) appended.event();
    assertThat(event.getRefundId()).isEqualTo("refund-1");
    assertThat(event.getRefundedAmountMinor()).isEqualTo(3000L);
  }

  @Test
  @DisplayName("같은 refundId 재요청 → 환불 1행 유지(이중 환불 없음) + refund.completed 재적재")
  void idempotentReuse() {
    service.process(command("refund-1", 3000L));
    service.process(command("refund-1", 3000L));

    assertThat(refundPort.saved()).hasSize(1);
    assertThat(refundPort.refundedAmount(PAYMENT_ID)).isEqualTo(3000L);
    assertThat(outboxAppender.appended())
        .hasSize(2)
        .allSatisfy(a -> assertThat(a.topic()).isEqualTo("refund.completed"));
  }

  @Test
  @DisplayName("초과 환불 거부 → refunds 적재 없음 + refund.failed 적재")
  void overRefundRejected() {
    service.process(command("refund-1", 12_000L));

    assertThat(refundPort.saved()).isEmpty();
    assertThat(refundPort.refundedAmount(PAYMENT_ID)).isZero();

    assertThat(outboxAppender.appended()).hasSize(1);
    Appended appended = outboxAppender.appended().get(0);
    assertThat(appended.topic()).isEqualTo("refund.failed");
    assertThat(appended.event()).isInstanceOf(RefundFailed.class);
    assertThat(((RefundFailed) appended.event()).getReason()).isEqualTo("REFUND_AMOUNT_EXCEEDED");
  }

  @Test
  @DisplayName("결제 없음 → refund.failed 적재")
  void paymentNotFound() {
    ProcessRefundCommand cmd =
        new ProcessRefundCommand(
            "order-unknown", PAYMENT_ID.toString(), "refund-x", 1000L, "customer", "refund-x");

    service.process(cmd);

    assertThat(refundPort.saved()).isEmpty();
    assertThat(outboxAppender.appended()).hasSize(1);
    Appended appended = outboxAppender.appended().get(0);
    assertThat(appended.topic()).isEqualTo("refund.failed");
    assertThat(((RefundFailed) appended.event()).getReason()).isEqualTo("PAYMENT_NOT_FOUND");
  }

  @Test
  @DisplayName("부분 환불 누적이 전체 한도 내면 모두 성공")
  void multiplePartialWithinLimit() {
    service.process(command("refund-1", 4000L));
    service.process(command("refund-2", 6000L));

    assertThat(refundPort.saved()).hasSize(2);
    assertThat(refundPort.refundedAmount(PAYMENT_ID)).isEqualTo(10_000L);
  }
}
