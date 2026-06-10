package com.rodminjo.commerce.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 이 서비스 전용 JPA 레포지토리만 스캔. outbox 레포지토리는 {@code OutboxAutoConfiguration}으로 자가 등록. */
@Configuration
@EnableJpaRepositories(basePackages = "com.rodminjo.commerce.payment")
public class JpaConfig {}
