package com.rodminjo.commerce.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  @Bean
  public NewTopic orderPlaced() {
    return TopicBuilder.name("order.placed").partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic orderCancelled() {
    return TopicBuilder.name("order.cancelled").partitions(3).replicas(1).build();
  }

  // Order is the Saga brain and the producer of payment.requested, so it owns the topic's creation.
  @Bean
  public NewTopic paymentRequested() {
    return TopicBuilder.name("payment.requested").partitions(3).replicas(1).build();
  }
}
