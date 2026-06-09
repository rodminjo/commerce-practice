package com.rodminjo.commerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Component scan stays broad to pick up common-infra beans under com.rodminjo.commerce.common.*.
// The outbox is NOT component-scanned: common-outbox ships as a Spring Boot auto-configuration
// (OutboxAutoConfiguration), so it wires itself from the classpath, is excluded from the scan by
// Boot's AutoConfigurationExcludeFilter (no double registration), and stays out of @WebMvcTest
// slices. It self-registers its own JPA repository/entity (scoped to its package), so this service
// keeps its own @EnableJpaRepositories/@EntityScan narrowed to com.rodminjo.commerce.order.
@SpringBootApplication(scanBasePackages = "com.rodminjo.commerce")
@EntityScan(basePackages = "com.rodminjo.commerce.order")
@EnableScheduling
public class OrderServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderServiceApplication.class, args);
  }
}
