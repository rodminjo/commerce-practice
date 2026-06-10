package com.rodminjo.commerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// 컴포넌트 스캔 범위: com.rodminjo.commerce.common.* 의 common-infra 빈 포함을 위해 넓게 설정.
// outbox는 스캔 대상 제외: common-outbox는 Spring Boot 자동 구성(OutboxAutoConfiguration)으로 동작하므로
// AutoConfigurationExcludeFilter에 의해 이중 등록 방지, @WebMvcTest 슬라이스 미포함.
// outbox JPA 리포지토리/엔티티는 자체 패키지 범위로 자동 등록되므로,
// 이 서비스의 @EnableJpaRepositories/@EntityScan은 com.rodminjo.commerce.order로 한정.
@SpringBootApplication(scanBasePackages = "com.rodminjo.commerce")
@EntityScan(basePackages = "com.rodminjo.commerce.order")
@EnableScheduling
public class OrderServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderServiceApplication.class, args);
  }
}
