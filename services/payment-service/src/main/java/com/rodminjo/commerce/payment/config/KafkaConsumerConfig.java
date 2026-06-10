package com.rodminjo.commerce.payment.config;

import com.rodminjo.commerce.events.payment.PaymentRequested;
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
 * Type-specific {@link ConcurrentKafkaListenerContainerFactory} for {@code payment.requested}. The
 * {@code SPECIFIC_PROTOBUF_VALUE_TYPE} makes {@code KafkaProtobufDeserializer} yield {@link
 * PaymentRequested} rather than a {@code DynamicMessage}.
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
  public ConcurrentKafkaListenerContainerFactory<String, PaymentRequested>
      paymentRequestedListenerContainerFactory() {
    return factory(PaymentRequested.class);
  }
}
