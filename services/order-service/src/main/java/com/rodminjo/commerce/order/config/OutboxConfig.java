package com.rodminjo.commerce.order.config;

import com.rodminjo.commerce.common.outbox.relay.OutboxTypeRegistry;
import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.order.OrderPlaced;
import com.rodminjo.commerce.events.payment.PaymentRequested;
import com.rodminjo.commerce.events.payment.RefundRequested;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

  @Bean
  public OutboxTypeRegistry outboxTypeRegistry() {
    OutboxTypeRegistry registry = new OutboxTypeRegistry();
    registry.register(OrderPlaced.getDescriptor());
    registry.register(OrderCancelled.getDescriptor());
    registry.register(PaymentRequested.getDescriptor());
    registry.register(RefundRequested.getDescriptor());
    return registry;
  }
}
