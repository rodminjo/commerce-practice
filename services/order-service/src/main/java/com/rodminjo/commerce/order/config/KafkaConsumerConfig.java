package com.rodminjo.commerce.order.config;

import com.rodminjo.commerce.events.inventory.InventoryReserved;
import com.rodminjo.commerce.events.payment.PaymentCompleted;
import com.rodminjo.commerce.events.payment.PaymentFailed;
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
 * 소비 Protobuf 타입별 {@link ConcurrentKafkaListenerContainerFactory} 1개씩 생성. {@code
 * KafkaProtobufDeserializer}가 {@code DynamicMessage} 대신 {@code SPECIFIC_PROTOBUF_VALUE_TYPE}을 통해 구체
 * 생성 클래스를 반환하도록 설정. 설정값은 {@link KafkaConsumerProperties}에서 바인딩.
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
  public ConcurrentKafkaListenerContainerFactory<String, InventoryReserved>
      inventoryReservedListenerContainerFactory() {
    return factory(InventoryReserved.class);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, PaymentCompleted>
      paymentCompletedListenerContainerFactory() {
    return factory(PaymentCompleted.class);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, PaymentFailed>
      paymentFailedListenerContainerFactory() {
    return factory(PaymentFailed.class);
  }
}
