package com.rodminjo.commerce.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans this service's own JPA repositories only. Kept as a standalone {@code @Configuration} (off
 * the main class) so {@code @WebMvcTest} slices don't bootstrap JPA. The outbox repository is no
 * longer caught by a broad {@code com.rodminjo.commerce} scan — it self-registers via {@code
 * OutboxAutoConfiguration} (Spring Boot auto-configuration).
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.rodminjo.commerce.order")
public class JpaConfig {}
