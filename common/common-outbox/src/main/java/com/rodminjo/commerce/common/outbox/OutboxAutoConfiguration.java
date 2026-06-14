package com.rodminjo.commerce.common.outbox;

import com.rodminjo.commerce.common.outbox.appender.JpaOutboxAppender;
import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.inbox.IdempotentConsumer;
import com.rodminjo.commerce.common.outbox.inbox.ProcessedEvent;
import com.rodminjo.commerce.common.outbox.inbox.ProcessedEventRepository;
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
 * 트랜잭셔널 아웃박스 빌딩 블록 Spring Boot 자동 구성. {@code common-outbox} 클래스패스 추가만으로 활성화. 소비 서비스는
 * {@code @Import} 또는 컴포넌트 스캔 확장 불필요.
 *
 * <p>{@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}로 등록.
 * {@code @Component}가 아닌 자동 구성이므로 {@code AutoConfigurationExcludeFilter}가 애플리케이션 컴포넌트 스캔에서 제외(이중 등록
 * 방지). {@code @WebMvcTest} 등 슬라이스 테스트에도 로드 안 됨. 아웃박스 JPA/Kafka 빈이 슬라이스 테스트로 누출되지 않음.
 *
 * <p>자체 JPA 리포지토리·엔티티 자체 등록({@code basePackageClasses} 이 패키지로 한정). 서비스는 자신의 패키지로
 * {@code @EnableJpaRepositories}/{@code @EntityScan} 범위 유지 가능.
 *
 * <p>소비 컨텍스트에서 필요한 협력 빈: {@link ClockHolder}, {@link KafkaTemplate}(Spring Boot Kafka 자동 구성 제공).
 * {@link OutboxTypeRegistry}는 빈 폴백으로 제공하여 컨텍스트 항상 기동 가능. 이벤트를 발행하는 서비스는 반드시 자체 레지스트리 빈(이벤트 디스크립터
 * 포함)을 등록해야 하며, 폴백보다 우선 적용. {@code @EnableScheduling}이 애플리케이션에 있어야 {@link OutboxRelayScheduler}
 * 활성화.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(OutboxRelayProperties.class)
@EnableJpaRepositories(
    basePackageClasses = {OutboxRepository.class, ProcessedEventRepository.class})
@EntityScan(basePackageClasses = {OutboxEvent.class, ProcessedEvent.class})
public class OutboxAutoConfiguration {

  /**
   * 빈 폴백 레지스트리. 모듈 임포트 시 컨텍스트 기동 실패 방지. 실제 이벤트를 발행하는 서비스는 이벤트 디스크립터가 채워진 자체 {@link
   * OutboxTypeRegistry} 빈으로 오버라이드.
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

  /**
   * 멱등 컨슈머 빈. 클래스패스 추가만으로 서비스 리스너가 {@link IdempotentConsumer#once} 로 메시지 중복 처리를 차단할 수 있다. 서비스는
   * {@code processed_event} 테이블(마이그레이션)을 자기 스키마에 추가해야 한다.
   */
  @Bean
  @ConditionalOnMissingBean
  public IdempotentConsumer idempotentConsumer(
      ProcessedEventRepository processedEventRepository, ClockHolder clockHolder) {
    return new IdempotentConsumer(processedEventRepository, clockHolder);
  }
}
