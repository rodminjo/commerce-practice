package com.rodminjo.commerce.payment.adapter.in.messaging;

import com.rodminjo.commerce.events.payment.PaymentRequested;
import com.rodminjo.commerce.payment.application.port.in.ProcessPaymentUseCase;
import com.rodminjo.commerce.payment.application.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentRequestedConsumer {

  private final ProcessPaymentUseCase processPaymentUseCase;

  @KafkaListener(
      topics = "payment.requested",
      containerFactory = "paymentRequestedListenerContainerFactory")
  public void onPaymentRequested(PaymentRequested event) {
    processPaymentUseCase.process(
        new ProcessPaymentCommand(
            event.getOrderId(),
            event.getAmountMinor(),
            event.getCurrency(),
            event.getIdempotencyKey()));
  }
}
