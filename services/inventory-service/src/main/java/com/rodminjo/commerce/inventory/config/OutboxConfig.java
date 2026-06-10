package com.rodminjo.commerce.inventory.config;

import com.rodminjo.commerce.common.outbox.relay.OutboxTypeRegistry;
import com.rodminjo.commerce.events.inventory.InventoryReleased;
import com.rodminjo.commerce.events.inventory.InventoryReserved;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

  @Bean
  public OutboxTypeRegistry outboxTypeRegistry() {
    OutboxTypeRegistry registry = new OutboxTypeRegistry();
    registry.register(InventoryReserved.getDescriptor());
    registry.register(InventoryReleased.getDescriptor());
    return registry;
  }
}
