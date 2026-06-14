package com.rodminjo.commerce.payment.application.service;

import com.google.protobuf.Timestamp;
import com.rodminjo.commerce.common.id.IdGenerator;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.payment.PaymentCompleted;
import com.rodminjo.commerce.events.payment.PaymentFailed;
import com.rodminjo.commerce.payment.application.port.in.ProcessPaymentUseCase;
import com.rodminjo.commerce.payment.application.port.out.SavePaymentPort;
import com.rodminjo.commerce.payment.config.PaymentSimulationProperties;
import com.rodminjo.commerce.payment.domain.model.Payment;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 요청 처리(모의 게이트웨이). {@code payments} 행 저장 및 {@code PaymentCompleted} 또는 {@code PaymentFailed}를 동일
 * 트랜잭션 내 outbox에 적재. 기본값은 성공. {@link PaymentSimulationProperties}로 테스트용 결정적 실패 주입 가능.
 */
@RequiredArgsConstructor
@Service
public class PaymentService implements ProcessPaymentUseCase {

  private static final String SIMULATED_FAILURE = "SIMULATED_PAYMENT_FAILURE";

  private final SavePaymentPort savePaymentPort;
  private final OutboxAppender outboxAppender;
  private final IdGenerator<UUID> idGenerator;
  private final ClockHolder clockHolder;
  private final PaymentSimulationProperties simulation;

  @Override
  @Transactional
  public void process(ProcessPaymentCommand command) {
    // 결제 비즈니스 멱등: 같은 idempotencyKey(=orderId) 결제가 이미 있으면 재처리하지 않는다(payments 단일 행 보장).
    if (savePaymentPort.existsByIdempotencyKey(command.idempotencyKey())) {
      return;
    }

    Payment payment =
        Payment.request(
            idGenerator.newId(),
            command.orderId(),
            command.amountMinor(),
            command.currency(),
            command.idempotencyKey());

    if (simulation.shouldFail(command.amountMinor())) {
      payment.fail();
      savePaymentPort.save(payment);
      outboxAppender.append(
          "Payment",
          payment.getOrderId(),
          "payment.failed",
          payment.getOrderId(),
          buildFailed(payment, SIMULATED_FAILURE));
      return;
    }

    payment.complete();
    savePaymentPort.save(payment);
    outboxAppender.append(
        "Payment",
        payment.getOrderId(),
        "payment.completed",
        payment.getOrderId(),
        buildCompleted(payment));
  }

  private PaymentCompleted buildCompleted(Payment payment) {
    return PaymentCompleted.newBuilder()
        .setOrderId(payment.getOrderId())
        .setPaymentId(payment.getId().toString())
        .setAmountMinor(payment.getAmount().amountMinor())
        .setOccurredAt(now())
        .build();
  }

  private PaymentFailed buildFailed(Payment payment, String reason) {
    return PaymentFailed.newBuilder()
        .setOrderId(payment.getOrderId())
        .setPaymentId(payment.getId().toString())
        .setReason(reason)
        .setOccurredAt(now())
        .build();
  }

  private Timestamp now() {
    Instant instant = clockHolder.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
