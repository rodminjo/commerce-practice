package com.rodminjo.commerce.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans this service's own JPA repositories only. The outbox repository self-registers via {@code
 * OutboxAutoConfiguration}.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.rodminjo.commerce.payment")
public class JpaConfig {}
