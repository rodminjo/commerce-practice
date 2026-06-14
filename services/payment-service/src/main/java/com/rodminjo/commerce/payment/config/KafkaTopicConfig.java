package com.rodminjo.commerce.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  @Bean
  public NewTopic paymentCompleted() {
    return TopicBuilder.name("payment.completed").partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic paymentFailed() {
    return TopicBuilder.name("payment.failed").partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic refundCompleted() {
    return TopicBuilder.name("refund.completed").partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic refundFailed() {
    return TopicBuilder.name("refund.failed").partitions(3).replicas(1).build();
  }
}
