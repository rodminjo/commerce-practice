package com.rodminjo.commerce.common.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "outbox")
public class OutboxEvent {

  @Id private UUID id;

  @Column(nullable = false)
  private String aggregateType;

  @Column(nullable = false)
  private String aggregateId;

  @Column(nullable = false)
  private String eventType;

  @Column(nullable = false)
  private String topic;

  @Column(nullable = false)
  private String partitionKey;

  @Column(nullable = false)
  private byte[] payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OutboxStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant publishedAt;

  /**
   * PENDING 상태 아웃박스 레코드 생성(attempts = 0). 새 이벤트의 유일한 생성 지점. PENDING/attempts 불변 조건을 분산 setter 호출 대신
   * 엔티티 내부에서 유지.
   */
  public static OutboxEvent pending(
      UUID id,
      String aggregateType,
      String aggregateId,
      String eventType,
      String topic,
      String partitionKey,
      byte[] payload,
      Instant createdAt) {
    OutboxEvent event = new OutboxEvent();
    event.id = id;
    event.aggregateType = aggregateType;
    event.aggregateId = aggregateId;
    event.eventType = eventType;
    event.topic = topic;
    event.partitionKey = partitionKey;
    event.payload = payload;
    event.status = OutboxStatus.PENDING;
    event.attempts = 0;
    event.createdAt = createdAt;
    return event;
  }

  public void markPublished(Instant now) {
    this.status = OutboxStatus.PUBLISHED;
    this.publishedAt = now;
  }

  public void incrementAttempts() {
    this.attempts++;
  }
}
