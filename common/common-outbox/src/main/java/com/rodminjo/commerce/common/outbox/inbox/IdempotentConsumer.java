package com.rodminjo.commerce.common.outbox.inbox;

import com.rodminjo.commerce.common.time.ClockHolder;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 멱등 처리 보장. {@code (consumerGroup, eventId)} dedup 레코드를 INSERT하여 같은 메시지를 컨슈머 그룹당 한 번만 처리한다.
 *
 * <p>핵심 정합성: dedup 행 INSERT와 핸들러 실행을 <b>같은 트랜잭션</b>에서 수행한다. 핸들러가 예외를 던지면 dedup 행까지 함께 롤백되므로(재시도 토픽
 * 등으로) 재전달되면 재처리가 가능하다. 즉 "성공 커밋된 경우에만 처리 기록이 남는다".
 *
 * <p>UNIQUE 충돌을 커밋 시점이 아니라 즉시 감지하기 위해 {@code saveAndFlush}를 사용한다.
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotentConsumer {

  private final ProcessedEventRepository repository;
  private final ClockHolder clockHolder;

  /** 신규 메시지면 handler 실행 후 true, 이미 처리된 메시지면 handler 미실행 후 false. */
  @Transactional
  public boolean once(String consumerGroup, UUID eventId, Runnable handler) {
    ProcessedEventId id = new ProcessedEventId(consumerGroup, eventId);

    // 사전 조회로 대다수 중복을 빠르게 걸러 false 반환(트랜잭션 오염 없음).
    if (repository.existsById(id)) {
      log.debug("중복 메시지 skip: consumerGroup={} eventId={}", consumerGroup, eventId);
      return false;
    }

    try {
      // 사전 조회 이후의 경합(동시 신규 도착)을 막는 안전망. UNIQUE 충돌을 commit 시점이 아니라 즉시 감지하려면 saveAndFlush.
      repository.saveAndFlush(ProcessedEvent.of(consumerGroup, eventId, clockHolder.now()));
    } catch (DataIntegrityViolationException e) {
      // 경합으로 다른 트랜잭션이 먼저 적재함. 이 트랜잭션은 이미 rollback-only 이므로 그대로 전파하여
      // 메시지를 재전달/재시도시킨다(재처리 시 existsById=true 로 깔끔히 skip). 핸들러는 실행하지 않음.
      log.warn("멱등 INSERT 경합 감지 — 재전달 유도: consumerGroup={} eventId={}", consumerGroup, eventId);
      throw e;
    }

    // 신규 메시지: 같은 트랜잭션에서 핸들러 실행. 예외 시 dedup 행 포함 전체 롤백 → 재처리 가능.
    handler.run();
    return true;
  }
}
