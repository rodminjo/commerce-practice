package com.rodminjo.commerce.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Enables Spring Data JPA repository scanning across the whole {@code com.rodminjo.commerce}
 * base package — not just this service's own package — so the reusable
 * {@code common-outbox} repositories ({@code OutboxRepository}) are registered alongside
 * the order-service repositories.
 *
 * <p>Kept as a standalone {@code @Configuration} (rather than on the main application class)
 * so that web-layer slice tests ({@code @WebMvcTest}) do not bootstrap JPA: the slice only
 * loads explicitly imported configuration, so this class stays out of those contexts.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.rodminjo.commerce")
public class JpaConfig {
}
