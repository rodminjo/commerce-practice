package com.rodminjo.commerce.common.outbox;

import com.rodminjo.commerce.common.outbox.appender.JpaOutboxAppender;
import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.relay.OutboxRelay;
import com.rodminjo.commerce.common.outbox.relay.OutboxRelayProperties;
import com.rodminjo.commerce.common.outbox.relay.OutboxRelayScheduler;
import com.rodminjo.commerce.common.outbox.relay.OutboxTypeRegistry;
import com.rodminjo.commerce.common.outbox.repository.OutboxRepository;
import com.rodminjo.commerce.common.time.ClockHolder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Spring Boot auto-configuration for the transactional outbox building blocks. Activates simply by
 * having {@code common-outbox} on the classpath — consuming services do not {@code @Import}
 * anything or widen their component scan to {@code com.rodminjo.commerce.*}; adding the dependency
 * is enough.
 *
 * <p>Registered via {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}. Because it is
 * an auto-configuration (not a {@code @Component}), Spring Boot's {@code
 * AutoConfigurationExcludeFilter} keeps it out of the application's component scan (no double
 * registration) and Boot test slices such as {@code @WebMvcTest} do not load it — so the outbox
 * JPA/Kafka beans never leak into slice tests.
 *
 * <p>Self-registers its own JPA repository and entity ({@code basePackageClasses} scoped to this
 * package), so services keep their own {@code @EnableJpaRepositories}/{@code @EntityScan} narrowed
 * to their own package.
 *
 * <p>Collaborators expected from the consuming context: a {@link ClockHolder} bean and a {@link
 * KafkaTemplate} (the latter from Spring Boot's Kafka auto-configuration). A {@link
 * OutboxTypeRegistry} is provided here as an empty fallback so the context always starts; a service
 * publishing events MUST contribute its own registry bean (with its event descriptors), which takes
 * precedence over the fallback. {@code @EnableScheduling} on the application activates {@link
 * OutboxRelayScheduler}.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(OutboxRelayProperties.class)
@EnableJpaRepositories(basePackageClasses = OutboxRepository.class)
@EntityScan(basePackageClasses = OutboxEvent.class)
public class OutboxAutoConfiguration {

  /**
   * Empty fallback registry so importing the module never breaks context startup. A service that
   * actually publishes events overrides this by declaring its own {@link OutboxTypeRegistry} bean
   * populated with its event descriptors.
   */
  @Bean
  @ConditionalOnMissingBean
  public OutboxTypeRegistry outboxTypeRegistry() {
    return new OutboxTypeRegistry();
  }

  @Bean
  @ConditionalOnMissingBean
  public JpaOutboxAppender jpaOutboxAppender(
      OutboxRepository outboxRepository, ClockHolder clockHolder) {
    return new JpaOutboxAppender(outboxRepository, clockHolder);
  }

  @Bean
  @ConditionalOnMissingBean
  public OutboxRelay outboxRelay(
      KafkaTemplate<String, Object> kafkaTemplate,
      OutboxRepository outboxRepository,
      OutboxTypeRegistry registry,
      ClockHolder clockHolder,
      OutboxRelayProperties properties) {
    return new OutboxRelay(kafkaTemplate, outboxRepository, registry, clockHolder, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public OutboxRelayScheduler outboxRelayScheduler(OutboxRelay outboxRelay) {
    return new OutboxRelayScheduler(outboxRelay);
  }
}
