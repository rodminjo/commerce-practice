package com.rodminjo.commerce.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans this service's own JPA repositories only. Standalone {@code @Configuration} (off the main
 * class) so {@code @WebMvcTest} slices don't bootstrap JPA. The outbox repository self-registers
 * via {@code OutboxAutoConfiguration}.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.rodminjo.commerce.inventory")
public class JpaConfig {}
