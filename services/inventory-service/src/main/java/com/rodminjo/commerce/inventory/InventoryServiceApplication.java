package com.rodminjo.commerce.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.scheduling.annotation.EnableScheduling;

// 컴포넌트 스캔 범위를 com.rodminjo.commerce 전체로 설정 — common-infra 빈 포함.
// common-outbox는 OutboxAutoConfiguration(클래스패스 자동 설정)으로 자가 등록.
// @EnableJpaRepositories/@EntityScan은 inventory 패키지만 한정.
// @EnableScheduling — outbox relay 스케줄러 활성화.
// @EnableKafkaRetryTopic — @RetryableTopic 논블로킹 재시도 토픽 인프라 활성화.
@SpringBootApplication(scanBasePackages = "com.rodminjo.commerce")
@EntityScan(basePackages = "com.rodminjo.commerce.inventory")
@EnableScheduling
@EnableKafkaRetryTopic
public class InventoryServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(InventoryServiceApplication.class, args);
  }
}
