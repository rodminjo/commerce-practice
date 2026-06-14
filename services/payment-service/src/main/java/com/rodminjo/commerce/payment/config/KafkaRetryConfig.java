package com.rodminjo.commerce.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;

/**
 * 논블로킹 재시도 토픽 + DLT 활성화. {@code @RetryableTopic} 리스너가 실패 시 {@code <topic>-retry-N}로 재전달되고 최종 실패 시
 * {@code <topic>-dlt}로 이동한다.
 */
@Configuration
@EnableKafkaRetryTopic
public class KafkaRetryConfig {}
