package com.rodminjo.commerce.payment.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인바운드 Protobuf 컨슈머 팩토리에 필요한 {@code spring.kafka.*} 하위 속성 바인딩. 분산된 {@code @Value} 대신 타입 안전한 전용 프로퍼티
 * 객체로 관리.
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
