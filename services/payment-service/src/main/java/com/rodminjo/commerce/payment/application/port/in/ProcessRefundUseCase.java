package com.rodminjo.commerce.payment.application.port.in;

public interface ProcessRefundUseCase {

  void process(ProcessRefundCommand command);

  record ProcessRefundCommand(
      String orderId,
      String paymentId,
      String refundId,
      long amountMinor,
      String reason,
      String idempotencyKey) {}
}
