package com.rodminjo.commerce.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 이 서비스 전용 JPA 리포지토리만 스캔. 메인 클래스 분리 {@code @Configuration} — {@code @WebMvcTest} 슬라이스에서 JPA 미부트스트랩.
 * 아웃박스 리포지토리는 {@code OutboxAutoConfiguration}이 자가 등록.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.rodminjo.commerce.inventory")
public class JpaConfig {}
