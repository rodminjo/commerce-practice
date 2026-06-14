package com.rodminjo.commerce.payment.adapter.in.messaging;

import com.rodminjo.commerce.common.outbox.inbox.EventIdHeader;
import com.rodminjo.commerce.common.outbox.inbox.IdempotentConsumer;
import com.rodminjo.commerce.events.payment.RefundRequested;
import com.rodminjo.commerce.payment.application.port.in.ProcessRefundUseCase;
import com.rodminjo.commerce.payment.application.port.in.ProcessRefundUseCase.ProcessRefundCommand;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * {@code refund.requested} 컨슈머. {@code x-event-id} 헤더 기반 멱등 처리 + 환불 비즈니스 멱등(refundId)으로 이중 환불을 막고,
 * 실패 시 {@code @RetryableTopic}으로 재시도 후 최종 실패 시 DLT로 보낸다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RefundRequestedConsumer {

  private static final String CONSUMER_GROUP = "payment-service";

  private final ProcessRefundUseCase processRefundUseCase;
  private final IdempotentConsumer idempotentConsumer;

  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoCreateTopics = "true")
  @KafkaListener(
      topics = "refund.requested",
      containerFactory = "refundRequestedListenerContainerFactory")
  public void onRefundRequested(
      RefundRequested event,
      @Header(name = EventIdHeader.HEADER, required = false) byte[] eventIdHeader) {
    UUID eventId = EventIdHeader.parse(eventIdHeader);
    idempotentConsumer.once(
        CONSUMER_GROUP,
        eventId,
        () ->
            processRefundUseCase.process(
                new ProcessRefundCommand(
                    event.getOrderId(),
                    event.getPaymentId(),
                    event.getRefundId(),
                    event.getAmountMinor(),
                    event.getReason(),
                    event.getIdempotencyKey())));
  }

  @DltHandler
  public void onDlt(RefundRequested event) {
    log.warn(
        "refund.requested DLT 도달 — 수동 개입 필요: orderId={} refundId={}",
        event.getOrderId(),
        event.getRefundId());
  }
}
