package com.rodminjo.commerce.payment;

import com.rodminjo.commerce.payment.config.PaymentSimulationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// 컴포넌트 스캔: common-infra 빈 수집을 위해 com.rodminjo.commerce 전체로 확장.
// common-outbox: OutboxAutoConfiguration(classpath 자동 구성)으로 자가 등록.
// @EnableJpaRepositories/@EntityScan: 이 서비스 범위(com.rodminjo.commerce.payment)로 한정.
// @EnableScheduling: outbox relay 스케줄러 활성화.
@SpringBootApplication(scanBasePackages = "com.rodminjo.commerce")
@EntityScan(basePackages = "com.rodminjo.commerce.payment")
@EnableConfigurationProperties(PaymentSimulationProperties.class)
@EnableScheduling
public class PaymentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentServiceApplication.class, args);
  }
}
