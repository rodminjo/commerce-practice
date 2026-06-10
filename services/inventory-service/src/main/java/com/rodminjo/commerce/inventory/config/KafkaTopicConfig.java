package com.rodminjo.commerce.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  @Bean
  public NewTopic inventoryReserved() {
    return TopicBuilder.name("inventory.reserved").partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic inventoryReleased() {
    return TopicBuilder.name("inventory.released").partitions(3).replicas(1).build();
  }
}
