package com.rodminjo.commerce.payment.application.port.out;

import com.rodminjo.commerce.payment.domain.model.Payment;

public interface SavePaymentPort {

  Payment save(Payment payment);
}
