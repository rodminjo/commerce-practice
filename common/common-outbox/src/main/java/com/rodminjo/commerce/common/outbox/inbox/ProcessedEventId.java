package com.rodminjo.commerce.common.outbox.inbox;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link ProcessedEvent} 복합키. {@code (consumerGroup, eventId)} 조합으로 컨슈머 그룹별 메시지 중복 처리를 식별한다. JPA
 * {@code @IdClass}용이므로 기본 생성자 + equals/hashCode 필수.
 */
public class ProcessedEventId implements Serializable {

  private String consumerGroup;
  private UUID eventId;

  public ProcessedEventId() {}

  public ProcessedEventId(String consumerGroup, UUID eventId) {
    this.consumerGroup = consumerGroup;
    this.eventId = eventId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessedEventId that)) {
      return false;
    }
    return Objects.equals(consumerGroup, that.consumerGroup)
        && Objects.equals(eventId, that.eventId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(consumerGroup, eventId);
  }
}
