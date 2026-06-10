package com.rodminjo.commerce.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Component scan stays broad to pick up common-infra beans under com.rodminjo.commerce.common.*.
// common-outbox wires itself via OutboxAutoConfiguration (classpath auto-config), so this service
// keeps its own @EnableJpaRepositories/@EntityScan narrowed to com.rodminjo.commerce.inventory.
// @EnableScheduling activates the outbox relay scheduler.
@SpringBootApplication(scanBasePackages = "com.rodminjo.commerce")
@EntityScan(basePackages = "com.rodminjo.commerce.inventory")
@EnableScheduling
public class InventoryServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(InventoryServiceApplication.class, args);
  }
}
