package com.rodminjo.commerce.payment;

import com.rodminjo.commerce.payment.config.PaymentSimulationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Component scan stays broad to pick up common-infra beans under com.rodminjo.commerce.common.*.
// common-outbox wires itself via OutboxAutoConfiguration (classpath auto-config); this service
// keeps
// its own @EnableJpaRepositories/@EntityScan narrowed to com.rodminjo.commerce.payment.
// @EnableScheduling activates the outbox relay scheduler.
@SpringBootApplication(scanBasePackages = "com.rodminjo.commerce")
@EntityScan(basePackages = "com.rodminjo.commerce.payment")
@EnableConfigurationProperties(PaymentSimulationProperties.class)
@EnableScheduling
public class PaymentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentServiceApplication.class, args);
  }
}
