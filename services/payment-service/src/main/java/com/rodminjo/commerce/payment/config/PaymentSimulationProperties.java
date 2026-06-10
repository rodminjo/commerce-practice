package com.rodminjo.commerce.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mock-payment behavior switch. By default every payment succeeds. Set {@code
 * payment.simulate.fail-for-amount} to a positive minor amount to force a {@code PaymentFailed} for
 * payments of exactly that amount — the deterministic hook the Saga's compensation tests use.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "payment.simulate")
public class PaymentSimulationProperties {

  /** Amount (minor units) that should fail; {@code -1} (default) means never fail. */
  private long failForAmount = -1;

  public boolean shouldFail(long amountMinor) {
    return failForAmount >= 0 && amountMinor == failForAmount;
  }
}
