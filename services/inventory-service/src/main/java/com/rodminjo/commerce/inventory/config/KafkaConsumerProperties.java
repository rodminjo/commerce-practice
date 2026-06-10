package com.rodminjo.commerce.inventory.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the subset of {@code spring.kafka.*} the inbound Protobuf consumer factories need. A
 * dedicated, type-safe properties object (rather than scattered {@code @Value}s) — it mirrors the
 * yaml shape so the schema-registry URL, bootstrap servers, and consumer group are read once and
 * shared by every per-type listener factory.
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
