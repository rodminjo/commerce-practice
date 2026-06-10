package com.rodminjo.commerce.payment.application.service.support;

import com.rodminjo.commerce.payment.application.port.out.SavePaymentPort;
import com.rodminjo.commerce.payment.domain.model.Payment;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link SavePaymentPort} test double. Records every saved {@link Payment} (a real domain
 * instance) so tests assert on stored state instead of Mockito interactions.
 */
public class FakeSavePaymentPort implements SavePaymentPort {

  private final List<Payment> saved = new ArrayList<>();

  @Override
  public Payment save(Payment payment) {
    saved.add(payment);
    return payment;
  }

  /** All saved payments, in call order. */
  public List<Payment> saved() {
    return saved;
  }

  /** The single saved payment; fails if not exactly one was saved. */
  public Payment single() {
    if (saved.size() != 1) {
      throw new IllegalStateException("expected exactly one saved payment but was " + saved.size());
    }
    return saved.get(0);
  }
}
