package com.rodminjo.commerce.payment.application.port.in;

public interface ProcessPaymentUseCase {

  void process(ProcessPaymentCommand command);

  record ProcessPaymentCommand(
      String orderId, long amountMinor, String currency, String idempotencyKey) {}
}
