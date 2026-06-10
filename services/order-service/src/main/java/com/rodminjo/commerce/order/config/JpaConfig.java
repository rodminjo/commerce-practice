package com.rodminjo.commerce.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 이 서비스 자체 JPA 리포지토리만 스캔. 메인 클래스와 분리된 독립 {@code @Configuration}으로 유지하여 {@code @WebMvcTest} 슬라이스에서
 * JPA가 부트스트랩되지 않도록 방지. outbox 리포지토리는 더 이상 넓은 {@code com.rodminjo.commerce} 스캔에 포함되지 않으며 {@code
 * OutboxAutoConfiguration}(Spring Boot 자동 구성)을 통해 자체 등록.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.rodminjo.commerce.order")
public class JpaConfig {}
