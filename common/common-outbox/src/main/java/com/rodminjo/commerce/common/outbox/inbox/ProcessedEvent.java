package com.rodminjo.commerce.common.outbox.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 멱등 컨슈머 처리 기록(inbox). {@code (consumerGroup, eventId)} 복합키로 컨슈머 그룹별 메시지를 한 번만 처리하도록 보장. 성공 커밋된
 * 경우에만 행이 남으므로(핸들러 실패 시 함께 롤백), 재전달 시 재처리가 가능하다.
 *
 * <p>단순 dedup 레코드이므로 {@code BaseEntity} 감사 컬럼은 두지 않는다. {@code processedAt}만 기록.
 *
 * <p>{@link Persistable} 구현으로 {@code isNew()=true}를 강제한다. 할당형 복합키 + {@code @Version} 부재 상태에서 Spring
 * Data {@code save()}는 엔티티를 "기존"으로 판단해 SELECT 후 UPDATE(=merge)를 수행하므로 중복 키여도 UNIQUE 충돌이 발생하지 않는다.
 * dedup은 INSERT 충돌로 중복을 감지해야 하므로 항상 {@code persist}(=INSERT)가 일어나도록 한다.
 */
@NoArgsConstructor
@Getter
@Entity
@IdClass(ProcessedEventId.class)
@Table(name = "processed_event")
public class ProcessedEvent implements Persistable<ProcessedEventId> {

  @Id
  @Column(nullable = false)
  private String consumerGroup;

  @Id
  @Column(nullable = false)
  private UUID eventId;

  @Column(nullable = false)
  private Instant processedAt;

  /** 처리 기록 생성. 신규 메시지 처리 직전에 INSERT 시도용. */
  public static ProcessedEvent of(String consumerGroup, UUID eventId, Instant processedAt) {
    ProcessedEvent event = new ProcessedEvent();
    event.consumerGroup = consumerGroup;
    event.eventId = eventId;
    event.processedAt = processedAt;
    return event;
  }

  @Override
  public ProcessedEventId getId() {
    return new ProcessedEventId(consumerGroup, eventId);
  }

  /** 항상 신규로 간주 → {@code save()}가 INSERT(persist)를 수행하여 중복 키 시 UNIQUE 충돌을 즉시 일으킨다. */
  @Override
  @Transient
  public boolean isNew() {
    return true;
  }
}
