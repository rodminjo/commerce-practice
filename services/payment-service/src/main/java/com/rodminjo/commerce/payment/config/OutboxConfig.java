package com.rodminjo.commerce.payment.config;

import com.rodminjo.commerce.common.outbox.relay.OutboxTypeRegistry;
import com.rodminjo.commerce.events.payment.PaymentCompleted;
import com.rodminjo.commerce.events.payment.PaymentFailed;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

  @Bean
  public OutboxTypeRegistry outboxTypeRegistry() {
    OutboxTypeRegistry registry = new OutboxTypeRegistry();
    registry.register(PaymentCompleted.getDescriptor());
    registry.register(PaymentFailed.getDescriptor());
    return registry;
  }
}
