package com.rodminjo.commerce.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@code @RetryableTopic} 논블로킹 재시도 토픽 인프라 활성화. 메인 애플리케이션 클래스가 아닌 별도 {@link Configuration}으로 분리하여
 * {@code @WebMvcTest} 등 슬라이스 테스트에 재시도 토픽 빈이 누출되지 않도록 한다.
 *
 * <p>{@code @EnableKafkaRetryTopic}은 백오프 스케줄링을 위한 {@link
 * org.springframework.scheduling.TaskScheduler}(또는 {@link RetryTopicSchedulerWrapper})를 요구한다. 전용
 * 스케줄러를 명시 제공하여 outbox 릴레이용 {@code @EnableScheduling} 스케줄러와 분리하고, 컨텍스트가 항상 기동되도록 한다.
 */
@Configuration
@EnableKafkaRetryTopic
public class KafkaRetryConfig {

  @Bean
  public RetryTopicSchedulerWrapper retryTopicSchedulerWrapper() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("kafka-retry-");
    scheduler.initialize();
    return new RetryTopicSchedulerWrapper(scheduler);
  }
}
