package com.rodminjo.commerce.inventory.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인바운드 Protobuf 컨슈머 팩토리에 필요한 {@code spring.kafka.*} 하위 설정 바인딩. 분산된 {@code @Value} 대신 타입 안전 프로퍼티 객체
 * 사용. yaml 구조를 그대로 반영하여 schema-registry URL, bootstrap 서버, 컨슈머 그룹을 한 번 읽고 모든 타입별 팩토리에 공유.
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
