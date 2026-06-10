package com.rodminjo.commerce.payment.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the subset of {@code spring.kafka.*} the inbound Protobuf consumer factory needs — a
 * dedicated, type-safe properties object instead of scattered {@code @Value}s.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "spring.kafka")
public class KafkaConsumerProperties {

  private String bootstrapServers;
  private final Consumer consumer = new Consumer();
  private final Map<String, String> properties = new HashMap<>();

  public String schemaRegistryUrl() {
    return properties.get("schema.registry.url");
  }

  @Getter
  @Setter
  public static class Consumer {
    private String groupId;
    private String autoOffsetReset = "earliest";
  }
}
