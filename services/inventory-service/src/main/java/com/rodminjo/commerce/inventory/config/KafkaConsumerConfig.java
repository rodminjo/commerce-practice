package com.rodminjo.commerce.inventory.config;

import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.order.OrderPlaced;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/**
 * 소비 Protobuf 타입별 {@link ConcurrentKafkaListenerContainerFactory} 등록. 각 팩토리에 {@code
 * SPECIFIC_PROTOBUF_VALUE_TYPE} 설정 → {@code KafkaProtobufDeserializer}가 {@code DynamicMessage} 대신
 * 구체 생성 클래스 반환. 리스너는 이름으로 팩토리 선택. 설정값은 {@link KafkaConsumerProperties}({@code spring.kafka.*}
 * 바인딩)에서 로드.
 */
@Configuration
@EnableConfigurationProperties(KafkaConsumerProperties.class)
public class KafkaConsumerConfig {

  private final KafkaConsumerProperties properties;

  public KafkaConsumerConfig(KafkaConsumerProperties properties) {
    this.properties = properties;
  }

  private <T> ConcurrentKafkaListenerContainerFactory<String, T> factory(Class<T> type) {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
    config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getConsumer().getGroupId());
    config.put(
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.getConsumer().getAutoOffsetReset());
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class);
    config.put(
        KafkaProtobufDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, properties.schemaRegistryUrl());
    config.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, type.getName());

    ConcurrentKafkaListenerContainerFactory<String, T> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
    return factory;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, OrderPlaced>
      orderPlacedListenerContainerFactory() {
    return factory(OrderPlaced.class);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, OrderCancelled>
      orderCancelledListenerContainerFactory() {
    return factory(OrderCancelled.class);
  }
}
