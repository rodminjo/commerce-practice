package com.rodminjo.commerce.payment.application.service;

import com.google.protobuf.Timestamp;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.payment.RefundCompleted;
import com.rodminjo.commerce.events.payment.RefundFailed;
import com.rodminjo.commerce.payment.application.port.in.ProcessRefundUseCase;
import com.rodminjo.commerce.payment.application.port.out.RefundPort;
import com.rodminjo.commerce.payment.domain.model.Payment;
import com.rodminjo.commerce.payment.domain.model.Refund;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 요청 처리. 멱등성 키(=refundId)로 이중 환불을 차단하고, 결제 잔액에 대한 원자적 조건부 차감으로 부분/전체 환불을 검증한다. 성공 시 {@code
 * RefundCompleted}, 실패(결제 없음·초과 환불) 시 {@code RefundFailed}를 동일 트랜잭션 내 outbox에 적재한다.
 *
 * <p>멱등 흐름: 같은 idempotencyKey 재요청이면 기존 환불 결과를 재사용(잔액 추가 차감 없음)하고 {@code RefundCompleted}를 다시 적재해
 * 사가가 결국 확정에 도달하도록 한다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RefundService implements ProcessRefundUseCase {

  private static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
  private static final String REFUND_AMOUNT_EXCEEDED = "REFUND_AMOUNT_EXCEEDED";

  private final RefundPort refundPort;
  private final OutboxAppender outboxAppender;
  private final ClockHolder clockHolder;

  @Override
  @Transactional
  public void process(ProcessRefundCommand command) {
    // 1) 결제 조회. 없으면 환불 불가 → 실패 이벤트.
    Optional<Payment> payment = refundPort.findPaymentByOrderId(command.orderId());
    if (payment.isEmpty()) {
      log.warn("환불 대상 결제 없음: orderId={} refundId={}", command.orderId(), command.refundId());
      appendFailed(command.orderId(), command.refundId(), PAYMENT_NOT_FOUND);
      return;
    }
    UUID paymentId = payment.get().getId();

    // 2) 멱등 게이트: 환불 레코드를 ON CONFLICT DO NOTHING으로 선점. 영향 행 0 = 이미 처리된 환불.
    //    동시 같은 refundId는 Postgres가 직렬화하여 패자가 예외 없이 0을 받고 이 분기로 흐른다(이중 환불 0).
    Refund refund =
        Refund.create(
            command.refundId(),
            paymentId.toString(),
            command.orderId(),
            command.amountMinor(),
            payment.get().getAmount().currency(), // 환불 통화 = 결제 통화
            command.idempotencyKey(),
            clockHolder.now());
    if (refundPort.insertIfAbsent(refund) == 0) {
      Refund existing = refundPort.findByIdempotencyKey(command.idempotencyKey()).orElse(refund);
      log.debug("멱등 환불 재사용: refundId={}", existing.getRefundId());
      appendCompleted(
          existing.getOrderId(),
          existing.getPaymentId(),
          existing.getRefundId(),
          existing.getAmount().amountMinor());
      return;
    }

    // 3) 선점 성공 → 결제 잔액 원자적 차감. 영향 행 0 = 초과 환불 → 선점 취소 후 실패 이벤트.
    if (refundPort.increaseRefundedAmount(paymentId, command.amountMinor()) == 0) {
      log.warn(
          "초과 환불 거부: orderId={} refundId={} amount={}",
          command.orderId(),
          command.refundId(),
          command.amountMinor());
      refundPort.deleteByRefundId(command.refundId());
      appendFailed(command.orderId(), command.refundId(), REFUND_AMOUNT_EXCEEDED);
      return;
    }

    // 4) 완료 이벤트.
    appendCompleted(
        command.orderId(), paymentId.toString(), command.refundId(), command.amountMinor());
  }

  private void appendCompleted(
      String orderId, String paymentId, String refundId, long refundedAmountMinor) {
    RefundCompleted event =
        RefundCompleted.newBuilder()
            .setOrderId(orderId)
            .setPaymentId(paymentId)
            .setRefundId(refundId)
            .setRefundedAmountMinor(refundedAmountMinor)
            .setOccurredAt(now())
            .build();
    outboxAppender.append("Payment", orderId, "refund.completed", orderId, event);
  }

  private void appendFailed(String orderId, String refundId, String reason) {
    RefundFailed event =
        RefundFailed.newBuilder()
            .setOrderId(orderId)
            .setRefundId(refundId)
            .setReason(reason)
            .setOccurredAt(now())
            .build();
    outboxAppender.append("Payment", orderId, "refund.failed", orderId, event);
  }

  private Timestamp now() {
    Instant instant = clockHolder.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
