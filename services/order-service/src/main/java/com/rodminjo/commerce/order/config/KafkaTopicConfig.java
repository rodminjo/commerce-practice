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

  // Order가 Saga 두뇌이자 payment.requested 생산자이므로 해당 토픽 생성 책임 보유.
  @Bean
  public NewTopic paymentRequested() {
    return TopicBuilder.name("payment.requested").partitions(3).replicas(1).build();
  }

  // Order가 환불 요청(refund.requested) 생산자이므로 해당 토픽 생성 책임 보유.
  @Bean
  public NewTopic refundRequested() {
    return TopicBuilder.name("refund.requested").partitions(3).replicas(1).build();
  }
}
